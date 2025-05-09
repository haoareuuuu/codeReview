package com.hsl.videstabilization.core.postprocess

import android.content.Context
import android.net.Uri
import com.hsl.videstabilization.api.StabilizationError
import com.hsl.videstabilization.api.StabilizationListener
import com.hsl.videstabilization.api.StabilizationParams
import com.hsl.videstabilization.api.StabilizerConfig
import com.hsl.videstabilization.codec.StabilizationFrameProcessor
import com.hsl.videstabilization.codec.VideoCodec
import com.hsl.videstabilization.codec.VideoTranscoder
import com.hsl.videstabilization.core.StabilizationTask
import com.hsl.videstabilization.core.StabilizationTask.TaskState
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 后处理视频防抖处理器
 */
class PostProcessStabilizer(
    private val context: Context,
    private val config: StabilizerConfig
) {
    // 执行器服务
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    // 当前任务
    private var currentTask: StabilizationTask? = null

    /**
     * 对视频进行防抖处理
     * @param inputVideo 输入视频的Uri
     * @param outputFile 输出视频的文件
     * @param params 防抖参数
     * @param listener 防抖监听器
     * @return 防抖任务
     */
    fun stabilize(
        inputVideo: Uri,
        outputFile: File,
        params: StabilizationParams,
        listener: StabilizationListener?
    ): StabilizationTask {
        // 创建新任务
        val task = StabilizationTask()

        // 设置任务状态为运行中
        task.setState(TaskState.RUNNING)

        // 保存当前任务
        currentTask = task

        // 在后台线程中执行防抖处理
        executorService.execute {
            try {
                // 检查输入视频是否有效
                if (!isValidInput(inputVideo)) {
                    task.setFailure(
                        StabilizationError(
                            StabilizationError.ERROR_INVALID_INPUT,
                            "Invalid input video"
                        )
                    )
                    return@execute
                }

                // 检查输出文件是否有效
                if (!isValidOutput(outputFile)) {
                    task.setFailure(
                        StabilizationError(
                            StabilizationError.ERROR_INVALID_OUTPUT,
                            "Invalid output file"
                        )
                    )
                    return@execute
                }

                // 创建视频编解码配置
                val codecConfig = VideoCodec.CodecConfig(
                    bitRate = params.outputBitRate,
                    frameRate = params.outputFrameRate,
                    keyFrameInterval = params.keyFrameInterval,
                    useHardwareAcceleration = params.useHardwareEncoder
                )

                // 创建视频转码器
                val transcoder = VideoTranscoder(context, inputVideo, outputFile, codecConfig)

                // 创建稳定化帧处理器
                val stabilizationProcessor = StabilizationFrameProcessor()

                // 设置稳定化参数
                // TODO: 根据params设置稳定化参数

                // 设置帧处理器
                transcoder.setFrameProcessor(stabilizationProcessor)

                // 设置回调
                transcoder.setCallback(object : VideoCodec.CodecCallback {
                    override fun onProgressUpdate(progress: Float) {
                        task.updateProgress(progress)
                        listener?.onProgressUpdate(progress)
                    }

                    override fun onComplete() {
                        // 转码完成
                    }

                    override fun onError(error: String, code: Int) {
                        task.setFailure(
                            StabilizationError(
                                code,
                                error
                            )
                        )
                        listener?.onError(StabilizationError(code, error))
                    }
                })

                // 初始化转码器
                if (!transcoder.initialize()) {
                    task.setFailure(
                        StabilizationError(
                            StabilizationError.ERROR_INITIALIZATION_FAILED,
                            "Failed to initialize transcoder"
                        )
                    )
                    return@execute
                }

                // 开始转码
                transcoder.start()

                // 等待转码完成
                while (transcoder.isRunning()) {
                    Thread.sleep(100)
                }

                // 释放资源
                transcoder.release()

                // 创建输出Uri
                val outputUri = Uri.fromFile(outputFile)

                // 设置任务成功
                task.setSuccess(outputUri)
                listener?.onComplete(outputUri)

            } catch (e: Exception) {
                // 设置任务失败
                val error = StabilizationError(
                    StabilizationError.ERROR_PROCESSING_FAILED,
                    "Failed to process video: ${e.message}",
                    e
                )
                task.setFailure(error)
                listener?.onError(error)
            }
        }

        return task
    }

    /**
     * 检查输入视频是否有效
     * @param inputVideo 输入视频的Uri
     * @return 是否有效
     */
    private fun isValidInput(inputVideo: Uri): Boolean {
        // 检查输入视频是否存在
        val inputStream = context.contentResolver.openInputStream(inputVideo)
        return inputStream?.use { true } ?: false
    }

    /**
     * 检查输出文件是否有效
     * @param outputFile 输出文件
     * @return 是否有效
     */
    private fun isValidOutput(outputFile: File): Boolean {
        // 检查输出文件的父目录是否存在，如果不存在则创建
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                return false
            }
        }

        // 如果输出文件已存在，则删除
        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                return false
            }
        }

        return true
    }

    /**
     * 取消当前任务
     */
    fun cancelCurrentTask() {
        currentTask?.cancel()
    }

    /**
     * 释放资源
     */
    fun release() {
        // 取消当前任务
        cancelCurrentTask()
        currentTask = null

        // 关闭执行器服务
        executorService.shutdown()
    }
}
