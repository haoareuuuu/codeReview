package com.hsl.videstabilization.algorithm.motion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils
import com.hsl.videstabilization.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import kotlin.math.min

/**
 * 基于特征点的运动估计器
 * 使用ORB特征点检测和匹配来估计运动
 */
class FeatureBasedMotionEstimator : BaseMotionEstimator() {
    companion object {
        private const val TAG = "FeatureMotionEstimator"

        // 特征点检测参数
        private const val MAX_FEATURES = 500
        private const val SCALE_FACTOR = 1.2f
        private const val PYRAMID_LEVELS = 8
        private const val EDGE_THRESHOLD = 31
        private const val FIRST_LEVEL = 0
        private const val WTA_K = 2
        private const val SCORE_TYPE = ORB.HARRIS_SCORE
        private const val PATCH_SIZE = 31
        private const val FAST_THRESHOLD = 20

        // RANSAC参数
        private const val RANSAC_REPROJ_THRESHOLD = 3.0
        private const val RANSAC_CONFIDENCE = 0.99
        private const val MIN_INLIER_RATIO = 0.5

        // 最小匹配点数
        private const val MIN_MATCHES = 10

        // 是否启用OpenCV
        private var OPENCV_INITIALIZED = false
    }

    // OpenCV相关对象
    private var orbDetector: ORB? = null
    private var matcher: DescriptorMatcher? = null

    // 上一帧的特征点和描述子
    private var prevKeypoints: MatOfKeyPoint? = null
    private var prevDescriptors: Mat? = null

    // 上一帧的灰度图像
    private var prevGray: Mat? = null

    // 临时矩阵
    private var rgbMat: Mat? = null
    private var grayMat: Mat? = null

    /**
     * 初始化OpenCV库
     */
    private fun initOpenCV() {
        if (!OPENCV_INITIALIZED) {
            if (OpenCVUtils.initSync()) {
                OPENCV_INITIALIZED = true
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize OpenCV")
                throw RuntimeException("OpenCV initialization failed")
            }
        }
    }

    override fun initialize(width: Int, height: Int) {
        super.initialize(width, height)

        // 初始化OpenCV
        initOpenCV()

        // 创建ORB特征检测器
        orbDetector = ORB.create(
            MAX_FEATURES,
            SCALE_FACTOR,
            PYRAMID_LEVELS,
            EDGE_THRESHOLD,
            FIRST_LEVEL,
            WTA_K,
            SCORE_TYPE,
            PATCH_SIZE,
            FAST_THRESHOLD
        )

        // 创建特征匹配器
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

        // 初始化矩阵
        rgbMat = Mat(height, width, CvType.CV_8UC3)
        grayMat = Mat(height, width, CvType.CV_8UC1)

        Log.d(TAG, "Feature-based motion estimator initialized")
    }

    override fun estimateMotion(prevFrame: Bitmap, currFrame: Bitmap): Matrix {
        if (!isInitialized) {
            initialize(prevFrame.width, prevFrame.height)
        }

        // 转换当前帧为OpenCV格式
        Utils.bitmapToMat(currFrame, rgbMat)
        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // 如果是第一帧，保存特征点并返回单位矩阵
        if (prevGray == null) {
            prevGray = Mat()
            grayMat!!.copyTo(prevGray)

            // 检测特征点
            prevKeypoints = MatOfKeyPoint()
            prevDescriptors = Mat()
            orbDetector!!.detectAndCompute(prevGray, Mat(), prevKeypoints, prevDescriptors)

            Log.d(TAG, "First frame processed, detected ${prevKeypoints!!.rows()} keypoints")
            return Matrix()
        }

        // 检测当前帧的特征点
        val currKeypoints = MatOfKeyPoint()
        val currDescriptors = Mat()
        orbDetector!!.detectAndCompute(grayMat, Mat(), currKeypoints, currDescriptors)

        // 如果没有足够的特征点，返回单位矩阵
        if (prevKeypoints!!.rows() < MIN_MATCHES || currKeypoints.rows() < MIN_MATCHES) {
            Log.w(TAG, "Not enough keypoints: prev=${prevKeypoints!!.rows()}, curr=${currKeypoints.rows()}")
            grayMat!!.copyTo(prevGray)
            prevKeypoints = currKeypoints
            prevDescriptors = currDescriptors
            return Matrix()
        }

        // 匹配特征点
        val matOfDMatch = MatOfDMatch()
        matcher!!.match(prevDescriptors, currDescriptors, matOfDMatch)
        val matches = matOfDMatch.toList()

        // 如果没有足够的匹配点，返回单位矩阵
        if (matches.size < MIN_MATCHES) {
            Log.w(TAG, "Not enough matches: ${matches.size}")
            grayMat!!.copyTo(prevGray)
            prevKeypoints = currKeypoints
            prevDescriptors = currDescriptors
            return Matrix()
        }

        // 筛选最佳匹配点
        val goodMatches = selectBestMatches(matches)

        // 如果没有足够的好匹配点，返回单位矩阵
        if (goodMatches.size < MIN_MATCHES) {
            Log.w(TAG, "Not enough good matches: ${goodMatches.size}")
            grayMat!!.copyTo(prevGray)
            prevKeypoints = currKeypoints
            prevDescriptors = currDescriptors
            return Matrix()
        }

        // 提取匹配点的坐标
        val prevPoints = ArrayList<Point>()
        val currPoints = ArrayList<Point>()

        for (match in goodMatches) {
            prevPoints.add(prevKeypoints!!.toArray()[match.queryIdx].pt)
            currPoints.add(currKeypoints.toArray()[match.trainIdx].pt)
        }

        val prevPointsMat = MatOfPoint2f()
        prevPointsMat.fromList(prevPoints)

        val currPointsMat = MatOfPoint2f()
        currPointsMat.fromList(currPoints)

        // 使用RANSAC算法估计变换矩阵
        val homography = findTransformMatrix(prevPointsMat, currPointsMat)

        // 转换为Android Matrix
        val transform = convertToAndroidMatrix(homography)

        // 验证变换矩阵
        val validTransform = validateTransform(transform)

        // 更新上一帧的数据
        grayMat!!.copyTo(prevGray)
        prevKeypoints = currKeypoints
        prevDescriptors = currDescriptors

        // 返回累积变换
        return accumulateTransform(validTransform)
    }

    /**
     * 选择最佳匹配点
     * @param matches 所有匹配点
     * @return 最佳匹配点列表
     */
    private fun selectBestMatches(matches: List<org.opencv.core.DMatch>): List<org.opencv.core.DMatch> {
        // 计算匹配距离的最小值和最大值
        var minDist = Double.MAX_VALUE
        var maxDist = 0.0

        for (match in matches) {
            val dist = match.distance.toDouble()
            if (dist < minDist) minDist = dist
            if (dist > maxDist) maxDist = dist
        }

        // 设置距离阈值
        val threshold = 3.0 * minDist

        // 筛选好的匹配点
        val goodMatches = ArrayList<org.opencv.core.DMatch>()
        for (match in matches) {
            if (match.distance < threshold) {
                goodMatches.add(match)
            }
        }

        // 限制匹配点数量
        val maxMatches = min(MAX_FEATURES, goodMatches.size)
        return if (goodMatches.isEmpty()) {
            goodMatches
        } else {
            goodMatches.subList(0, maxMatches)
        }
    }

    /**
     * 使用RANSAC算法找到变换矩阵
     * @param prevPoints 前一帧的特征点
     * @param currPoints 当前帧的特征点
     * @return 变换矩阵
     */
    private fun findTransformMatrix(prevPoints: MatOfPoint2f, currPoints: MatOfPoint2f): Mat {
        // 使用RANSAC算法估计变换矩阵
        val mask = MatOfByte()
        val homography = Calib3d.findHomography(
            prevPoints,
            currPoints,
            Calib3d.RANSAC,
            RANSAC_REPROJ_THRESHOLD,
            mask
        )

        // 如果变换矩阵为空，返回单位矩阵
        if (homography.empty()) {
            Log.w(TAG, "Failed to find homography")
            return Mat.eye(3, 3, CvType.CV_64F)
        }

        // 计算内点比例
        val inlierRatio = Core.countNonZero(mask) / mask.total().toDouble()

        // 如果内点比例太低，返回单位矩阵
        if (inlierRatio < MIN_INLIER_RATIO) {
            Log.w(TAG, "Low inlier ratio: $inlierRatio")
            return Mat.eye(3, 3, CvType.CV_64F)
        }

        return homography
    }

    /**
     * 将OpenCV的Mat转换为Android的Matrix
     * @param homography OpenCV的变换矩阵
     * @return Android的变换矩阵
     */
    private fun convertToAndroidMatrix(homography: Mat): Matrix {
        val values = FloatArray(9)

        // 提取变换矩阵的值
        for (i in 0..2) {
            for (j in 0..2) {
                values[i * 3 + j] = homography.get(i, j)[0].toFloat()
            }
        }

        // 归一化矩阵
        val scale = values[8]
        if (scale != 0f && scale != 1f) {
            for (i in 0..8) {
                values[i] /= scale
            }
        }

        // 创建Android Matrix
        return Matrix().apply {
            setValues(values)
        }
    }

    override fun reset() {
        super.reset()

        // 重置OpenCV相关对象
        prevGray = null
        prevKeypoints = null
        prevDescriptors = null
    }

    override fun release() {
        // 释放OpenCV相关对象
        prevGray?.release()
        prevKeypoints?.release()
        prevDescriptors?.release()
        rgbMat?.release()
        grayMat?.release()

        prevGray = null
        prevKeypoints = null
        prevDescriptors = null
        rgbMat = null
        grayMat = null
        orbDetector = null
        matcher = null

        Log.d(TAG, "Resources released")
    }
}
