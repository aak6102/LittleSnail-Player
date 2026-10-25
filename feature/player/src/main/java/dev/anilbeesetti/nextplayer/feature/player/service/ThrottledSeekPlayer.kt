package dev.anilbeesetti.nextplayer.feature.player.service

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.ceil

/**
 * 快进节流播放器（最小改动版）。
 *
 * 在完全保留 Next Player 原生能力的前提下，对「跳转(seek)」增加最小时间间隔限制：
 * 任意两次跳转之间必须间隔 [SEEK_INTERVAL_MS]（默认 2 分钟）。
 *
 * 设计要点：
 * - 仅覆写 [handleSeek]，其余所有播放能力（播放/暂停/倍速/字幕/解码器/通知栏/蓝牙控件）
 *   全部透传 [ForwardingSimpleBasePlayer] 的默认实现，与原生行为一致。
 * - 视频自然播放至结尾后从头循环（positionMs == 0）永远放行，否则单集循环会被卡住。
 * - 冷却状态保存在 **companion object 静态字段** 中：跨 [ThrottledSeekPlayer] 实例重建、
 *   跨 [PlayerService] 重建、跨「退出视频再进入」均持续生效，杜绝通过退出生效重置冷却的漏洞。
 * - 冷却期内（含退出再进入同一视频、切换新视频）的跳转请求一律被丢弃（播放位置不变化，
 *   UI 进度条自动回弹），并通过 [onSeekThrottled] 回调告知用户还需等待多久。
 */
@UnstableApi
class ThrottledSeekPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {

    /** 两次快进之间的最小间隔：2 分钟 */
    private val seekIntervalMs = SEEK_INTERVAL_MS

    /**
     * 冷却期内被拦截的跳转会通过此回调通知上层（例如弹 Toast 提示剩余等待时间）。
     * 参数为「还需等待的毫秒数」。
     */
    var onSeekThrottled: ((remainingMillis: Long) -> Unit)? = null

    /** 暴露底层真实 ExoPlayer，供 PlayerService 内部需要 ExoPlayer 类型的能力使用 */
    val realPlayer: ExoPlayer
        get() = player as ExoPlayer

    /** 上次提示的时间戳，用于给提示去抖，避免拖动进度条时连续刷屏 */
    @Volatile
    private var lastNotifyAtMs = 0L

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        // 循环播放（positionMs == 0）一律放行（Media3 内部循环/起点定位依赖此调用）
        if (positionMs == 0L) {
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
        val now = System.currentTimeMillis()
        // 冷却期内：吞掉本次跳转，播放位置保持不变，并提示剩余等待时间。
        // 注意：此处不区分「是否新视频/是否重新进入」，冷却状态为全局静态，退出再进入也不会重置。
        if (now - lastSeekAtMs < seekIntervalMs) {
            val remainingMillis = seekIntervalMs - (now - lastSeekAtMs)
            if (now - lastNotifyAtMs > SEEK_THROTTLE_NOTIFY_INTERVAL_MS) {
                lastNotifyAtMs = now
                onSeekThrottled?.invoke(remainingMillis)
            }
            return Futures.immediateVoidFuture()
        }
        lastSeekAtMs = now
        return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
    }

    /**
     * 不经过冷却限制的「程序化跳转」（用于恢复断点进度）。
     * 直接对底层 ExoPlayer 执行 seek，绕开 [handleSeek]，因此既不会被冷却拦截，
     * 也不会刷新冷却时间戳 [lastSeekAtMs]，确保「恢复进度」与「用户手动快进」互不干扰。
     */
    fun seekToWithoutThrottle(positionMs: Long) {
        realPlayer.seekTo(positionMs)
    }

    companion object {
        /**
         * 上次成功快进的时间戳（全局静态）。
         * 初始化为「负间隔」，保证 App 启动后的首次跳转不被拦截。
         * 静态字段在 ThrottledSeekPlayer / PlayerService 重建后依然保留，
         * 因此「退出视频再进入」无法重置冷却。
         */
        @Volatile
        private var lastSeekAtMs = -SEEK_INTERVAL_MS
    }
}

/** 两次快进之间的最小间隔（毫秒）：默认 2 分钟 */
const val SEEK_INTERVAL_MS: Long = 120_000L

/** 冷却提示的去抖间隔（毫秒）：两次提示之间至少间隔 1.5 秒 */
const val SEEK_THROTTLE_NOTIFY_INTERVAL_MS: Long = 1_500L

/** 把毫秒转为向上取整的秒数（用于提示文案） */
fun seekMillisToSeconds(millis: Long): Int = ceil(millis / 1000.0).toInt()
