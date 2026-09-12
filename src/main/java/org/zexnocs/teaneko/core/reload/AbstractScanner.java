package org.zexnocs.teaneko.core.reload;

import org.zexnocs.teaneko.core.reload.api.IScanner;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为可重载扫描器提供只初始化一次和失败后可重试的通用状态管理。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
public abstract class AbstractScanner implements IScanner {

    /// 是否已经初始化过了。用于防止第一次重复加载。
    private final AtomicBoolean isInit = new AtomicBoolean(false);

    /**
     * 扫描方法。
     *
     */
    protected abstract void _scan();

    /**
     * 清理原始数据的方法。
     *
     */
    protected abstract void _clear();

    /**
     * 热重载方法。
     */
    @Override
    public synchronized void reload() {
        _clear();
        _scan();
    }

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    @Override
    public synchronized void init() {
        if(isInit.compareAndSet(false, true)) {
            try {
                _scan();
            } catch (RuntimeException | Error exception) {
                // 首次扫描失败不能永久锁死初始化状态，允许上层在处理异常后再次尝试。
                isInit.set(false);
                throw exception;
            }
        }
    }
}
