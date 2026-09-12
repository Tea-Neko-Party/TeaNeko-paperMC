package org.zexnocs.teanekopapermc.core.initializer;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekopapermc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teanekopapermc.core.initializer.api.TeaNekoInitializer;

import java.util.Comparator;
import java.util.List;

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
    /**
     *  按优先级从高到低排列的初始化器定义快照。
     *  <p>优先级相同时按初始化器实际类名排序，确保每次启动顺序稳定。
     */
    @Getter
    private volatile List<InitializerDefinition> initializerDefinitions = List.of();

    private final IBeanScanner iBeanScanner;

    @Autowired
    public TeaNekoInitializerScanner(IBeanScanner iBeanScanner) {
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 扫描方法。
     *
     */
    @Override
    protected synchronized void _scan() {
        // 保留注解元数据，处理器需要据此确定执行顺序和失败策略。
        initializerDefinitions = iBeanScanner
                .getBeansWithAnnotationAndInterface(
                        TeaNekoInitializer.class,
                        ITeaNekoInitializer.class
                )
                .values()
                .stream()
                .map(pair -> new InitializerDefinition(pair.first(), pair.second()))
                .sorted(Comparator
                        .comparingInt((InitializerDefinition definition) ->
                                definition.metadata().priority())
                        .reversed()
                        .thenComparing(definition -> iBeanScanner
                                .getBeanClass(definition.initializer())
                                .getName()))
                .toList();
    }

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected synchronized void _clear() {
        initializerDefinitions = List.of();
    }

    /**
     * 保存初始化器实例及其启动元数据。
     *
     * @param metadata 初始化器注解元数据
     * @param initializer 初始化器实例
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    public record InitializerDefinition(TeaNekoInitializer metadata,
                                        ITeaNekoInitializer initializer) {
    }
}
