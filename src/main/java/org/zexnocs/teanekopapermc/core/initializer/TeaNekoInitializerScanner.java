package org.zexnocs.teanekopapermc.core.initializer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekopapermc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teanekopapermc.core.initializer.api.TeaNekoInitializer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 负责扫描和注册所有标记了
 * {@link org.zexnocs.teanekopapermc.core.initializer.api.TeaNekoInitializer}
 * 注解的类，并将它们注册到 PaperMC 初始化器中。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
@Service
public class TeaNekoInitializerScanner extends AbstractScanner {
    /// 初始化器集合
    private final Set<ITeaNekoInitializer> initializerSet = ConcurrentHashMap.newKeySet();
    private final IBeanScanner iBeanScanner;

    @Autowired
    public TeaNekoInitializerScanner(IBeanScanner iBeanScanner) {
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 获取所有注册的初始化器的 copy 集合。
     *
     * @return 所有注册的初始化器的 copy 集合
     */
    public Set<ITeaNekoInitializer> getInitializerSet() {
        return Set.copyOf(initializerSet);
    }

    /**
     * 扫描方法。
     *
     */
    @Override
    protected void _scan() {
        // 使用 bean 扫描器扫描所有标记了 TeaNekoInitializer 注解的类，并将它们注册到 initializerSet 中
        initializerSet.addAll(
                iBeanScanner.getBeansWithAnnotationAndInterface(TeaNekoInitializer.class, ITeaNekoInitializer.class)
                        .values()
                        .stream()
                        .map(Pair::second)
                        .collect(Collectors.toSet())
        );

    }

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        initializerSet.clear();
    }
}
