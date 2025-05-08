package com.hsl.videstabilization.algorithm.motion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils
import com.hsl.videstabilization.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * 基于光流的运动估计器
 * 使用Lucas-Kanade光流算法估计运动
 */
class OpticalFlowMotionEstimator : BaseMotionEstimator() {
    companion object {
        private const val TAG = "OpticalFlowEstimator"

        // 光流参数
        private const val MAX_CORNERS = 500
        private const val QUALITY_LEVEL = 0.01
        private const val MIN_DISTANCE = 10.0
        private const val BLOCK_SIZE = 3
        private const val USE_HARRIS_DETECTOR = false
        private const val K = 0.04

        // 光流金字塔参数
        private const val MAX_PYRAMID_LEVEL = 3
        private const val WINDOW_SIZE = 15

        // 终止条件
        private const val MAX_ITERATIONS = 30
        private const val EPSILON = 0.01

        // RANSAC参数
        private const val RANSAC_REPROJ_THRESHOLD = 3.0
        private const val MIN_INLIER_RATIO = 0.5

        // 最小跟踪点数
        private const val MIN_TRACKED_POINTS = 10

        // 是否启用OpenCV
        private var OPENCV_INITIALIZED = false
    }

    // 上一帧的灰度图像
    private var prevGray: Mat? = null

    // 上一帧的特征点
    private var prevPoints: MatOfPoint2f? = null

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

        // 初始化矩阵
        rgbMat = Mat(height, width, CvType.CV_8UC3)
        grayMat = Mat(height, width, CvType.CV_8UC1)

        Log.d(TAG, "Optical flow motion estimator initialized")
    }

    override fun estimateMotion(prevFrame: Bitmap, currFrame: Bitmap): Matrix {
        if (!isInitialized) {
            initialize(prevFrame.width, prevFrame.height)
        }

        // 转换当前帧为OpenCV格式
        Utils.bitmapToMat(currFrame, rgbMat)
        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // 如果是第一帧，保存图像并返回单位矩阵
        if (prevGray == null) {
            prevGray = Mat()
            grayMat!!.copyTo(prevGray)

            // 检测特征点
            prevPoints = detectFeaturePoints(prevGray!!)

            Log.d(TAG, "First frame processed, detected ${prevPoints!!.rows()} points")
            return Matrix()
        }

        // 如果没有足够的特征点，重新检测
        if (prevPoints == null || prevPoints!!.rows() < MIN_TRACKED_POINTS) {
            prevPoints = detectFeaturePoints(prevGray!!)

            // 如果仍然没有足够的特征点，返回单位矩阵
            if (prevPoints!!.rows() < MIN_TRACKED_POINTS) {
                Log.w(TAG, "Not enough feature points: ${prevPoints!!.rows()}")
                grayMat!!.copyTo(prevGray)
                return Matrix()
            }
        }

        // 使用光流算法跟踪特征点
        val nextPoints = MatOfPoint2f()
        val status = MatOfByte()
        val err = MatOfFloat()

        Video.calcOpticalFlowPyrLK(
            prevGray,
            grayMat,
            prevPoints,
            nextPoints,
            status,
            err,
            Size(WINDOW_SIZE.toDouble(), WINDOW_SIZE.toDouble()),
            MAX_PYRAMID_LEVEL,
            TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, MAX_ITERATIONS, EPSILON),
            0,
            0.001
        )

        // 筛选成功跟踪的点
        val statusArr = status.toArray()
        val prevPointsArr = prevPoints!!.toArray()
        val nextPointsArr = nextPoints.toArray()

        val trackedPrevPoints = ArrayList<Point>()
        val trackedNextPoints = ArrayList<Point>()

        for (i in statusArr.indices) {
            if (statusArr[i] == 1.toByte()) {
                trackedPrevPoints.add(prevPointsArr[i])
                trackedNextPoints.add(nextPointsArr[i])
            }
        }

        // 如果没有足够的跟踪点，返回单位矩阵
        if (trackedPrevPoints.size < MIN_TRACKED_POINTS) {
            Log.w(TAG, "Not enough tracked points: ${trackedPrevPoints.size}")
            grayMat!!.copyTo(prevGray)
            prevPoints = detectFeaturePoints(prevGray!!)
            return Matrix()
        }

        // 转换为MatOfPoint2f
        val trackedPrevPointsMat = MatOfPoint2f()
        trackedPrevPointsMat.fromList(trackedPrevPoints)

        val trackedNextPointsMat = MatOfPoint2f()
        trackedNextPointsMat.fromList(trackedNextPoints)

        // 使用RANSAC算法估计变换矩阵
        val transform = findTransformMatrix(trackedPrevPointsMat, trackedNextPointsMat)

        // 验证变换矩阵
        val validTransform = validateTransform(transform)

        // 更新上一帧的数据
        grayMat!!.copyTo(prevGray)
        prevPoints = MatOfPoint2f()
        trackedNextPointsMat.copyTo(prevPoints)

        // 如果跟踪点太少，重新检测特征点
        if (prevPoints!!.rows() < MIN_TRACKED_POINTS / 2) {
            prevPoints = detectFeaturePoints(prevGray!!)
        }

        // 返回累积变换
        return accumulateTransform(validTransform)
    }

    /**
     * 检测特征点
     * @param gray 灰度图像
     * @return 特征点
     */
    private fun detectFeaturePoints(gray: Mat): MatOfPoint2f {
        val corners = MatOfPoint()

        // 使用Shi-Tomasi角点检测算法
        Imgproc.goodFeaturesToTrack(
            gray,
            corners,
            MAX_CORNERS,
            QUALITY_LEVEL,
            MIN_DISTANCE,
            Mat(),
            BLOCK_SIZE,
            USE_HARRIS_DETECTOR,
            K
        )

        // 转换为MatOfPoint2f
        val cornerPoints = MatOfPoint2f()
        corners.convertTo(cornerPoints, CvType.CV_32FC2)

        return cornerPoints
    }

    /**
     * 使用RANSAC算法找到变换矩阵
     * @param prevPoints 前一帧的特征点
     * @param currPoints 当前帧的特征点
     * @return Android的变换矩阵
     */
    private fun findTransformMatrix(prevPoints: MatOfPoint2f, currPoints: MatOfPoint2f): Matrix {
        // 使用RANSAC算法估计仿射变换
        val mask = MatOfByte()
        // 使用findHomography代替estimateAffinePartial2D
        val homography = Calib3d.findHomography(
            prevPoints,
            currPoints,
            Calib3d.RANSAC,
            RANSAC_REPROJ_THRESHOLD,
            mask
        )

        // 从单应性矩阵提取仿射变换
        val affine = Mat(2, 3, CvType.CV_64F)

        // 如果单应性矩阵为空，返回单位矩阵
        if (homography.empty()) {
            Log.w(TAG, "Failed to find homography")
            return Matrix()
        }

        // 从单应性矩阵提取仿射变换部分
        // 仿射变换是单应性矩阵的前两行
        for (i in 0..1) {
            for (j in 0..2) {
                affine.put(i, j, homography.get(i, j)[0])
            }
        }

        // 如果变换矩阵为空，返回单位矩阵
        if (affine.empty()) {
            Log.w(TAG, "Failed to find affine transform")
            return Matrix()
        }

        // 计算内点比例
        val inlierRatio = Core.countNonZero(mask) / mask.total().toDouble()

        // 如果内点比例太低，返回单位矩阵
        if (inlierRatio < MIN_INLIER_RATIO) {
            Log.w(TAG, "Low inlier ratio: $inlierRatio")
            return Matrix()
        }

        // 转换为Android Matrix
        return convertToAndroidMatrix(affine)
    }

    /**
     * 将OpenCV的Mat转换为Android的Matrix
     * @param affine OpenCV的仿射变换矩阵
     * @return Android的变换矩阵
     */
    private fun convertToAndroidMatrix(affine: Mat): Matrix {
        // 仿射变换矩阵是2x3的，需要转换为3x3的齐次变换矩阵
        val values = FloatArray(9)

        // 设置默认值为单位矩阵
        values[0] = 1f; values[1] = 0f; values[2] = 0f
        values[3] = 0f; values[4] = 1f; values[5] = 0f
        values[6] = 0f; values[7] = 0f; values[8] = 1f

        // 提取仿射变换矩阵的值
        if (!affine.empty() && affine.rows() == 2 && affine.cols() == 3) {
            // 第一行
            values[0] = affine.get(0, 0)[0].toFloat()
            values[1] = affine.get(0, 1)[0].toFloat()
            values[2] = affine.get(0, 2)[0].toFloat()

            // 第二行
            values[3] = affine.get(1, 0)[0].toFloat()
            values[4] = affine.get(1, 1)[0].toFloat()
            values[5] = affine.get(1, 2)[0].toFloat()
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
        prevPoints = null
    }

    override fun release() {
        // 释放OpenCV相关对象
        prevGray?.release()
        prevPoints?.release()
        rgbMat?.release()
        grayMat?.release()

        prevGray = null
        prevPoints = null
        rgbMat = null
        grayMat = null

        Log.d(TAG, "Resources released")
    }
}
