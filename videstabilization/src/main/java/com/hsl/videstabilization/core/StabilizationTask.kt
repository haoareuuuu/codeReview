package com.hsl.videstabilization.core

import android.net.Uri
import com.hsl.videstabilization.api.StabilizationError
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 视频防抖任务类
 * 用于跟踪和控制后处理防抖任务
 */
class StabilizationTask {
    // 任务状态
    private var state: TaskState = TaskState.PENDING
    
    // 进度监听器
    private var progressListener: ((Float) -> Unit)? = null
    
    // 成功监听器
    private var successListener: ((Uri) -> Unit)? = null
    
    // 失败监听器
    private var failureListener: ((StabilizationError) -> Unit)? = null
    
    // 执行器
    private val executor: Executor = Executors.newSingleThreadExecutor()
    
    /**
     * 添加进度监听器
     * @param listener 监听器
     * @return 任务实例
     */
    fun addOnProgressListener(listener: (Float) -> Unit): StabilizationTask {
        this.progressListener = listener
        return this
    }
    
    /**
     * 添加成功监听器
     * @param listener 监听器
     * @return 任务实例
     */
    fun addOnSuccessListener(listener: (Uri) -> Unit): StabilizationTask {
        this.successListener = listener
        return this
    }
    
    /**
     * 添加失败监听器
     * @param listener 监听器
     * @return 任务实例
     */
    fun addOnFailureListener(listener: (StabilizationError) -> Unit): StabilizationTask {
        this.failureListener = listener
        return this
    }
    
    /**
     * 取消任务
     * @return 是否成功取消
     */
    fun cancel(): Boolean {
        if (state == TaskState.RUNNING) {
            state = TaskState.CANCELLED
            return true
        }
        return false
    }
    
    /**
     * 更新进度
     * @param progress 进度值
     */
    internal fun updateProgress(progress: Float) {
        executor.execute {
            progressListener?.invoke(progress)
        }
    }
    
    /**
     * 设置任务成功
     * @param outputUri 输出视频的Uri
     */
    internal fun setSuccess(outputUri: Uri) {
        executor.execute {
            state = TaskState.COMPLETED
            successListener?.invoke(outputUri)
        }
    }
    
    /**
     * 设置任务失败
     * @param error 错误信息
     */
    internal fun setFailure(error: StabilizationError) {
        executor.execute {
            state = TaskState.FAILED
            failureListener?.invoke(error)
        }
    }
    
    /**
     * 设置任务状态
     * @param newState 新状态
     */
    internal fun setState(newState: TaskState) {
        this.state = newState
    }
    
    /**
     * 获取任务状态
     * @return 任务状态
     */
    fun getState(): TaskState {
        return state
    }
    
    /**
     * 任务状态枚举
     */
    enum class TaskState {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
