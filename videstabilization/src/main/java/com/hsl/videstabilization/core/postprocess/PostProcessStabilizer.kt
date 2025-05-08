package com.hsl.videstabilization.core.postprocess

import android.content.Context
import android.net.Uri
import com.hsl.videstabilization.api.StabilizationError
import com.hsl.videstabilization.api.StabilizationListener
import com.hsl.videstabilization.api.StabilizationParams
import com.hsl.videstabilization.api.StabilizerConfig
import com.hsl.videstabilization.core.StabilizationTask
import com.hsl.videstabilization.core.StabilizationTask.TaskState
import com.hsl.videstabilization.core.postprocess.VideoProcessor
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

                // 创建视频处理器
                val processor = VideoProcessor(context, config, params)

                // 设置进度回调
                processor.setProgressCallback { progress ->
                    task.updateProgress(progress)
                    listener?.onProgressUpdate(progress)
                }

                // 处理视频
                val outputUri = processor.process(inputVideo, outputFile)

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
     * 释放资源
     */
    fun release() {
        // 取消当前任务
        currentTask?.cancel()
        currentTask = null

        // 关闭执行器服务
        executorService.shutdown()
    }
}
