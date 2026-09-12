package org.zexnocs.teaneko.mc.core.initializer;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.zexnocs.teaneko.core.framework.pair.IndependentPair;
import org.zexnocs.teaneko.core.framework.pair.Pair;
import org.zexnocs.teaneko.core.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teaneko.mc.core.initializer.api.ITeaNekoInitializer;
import org.zexnocs.teaneko.mc.core.initializer.api.TeaNekoInitializer;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Paper 初始化器扫描顺序和默认失败策略。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class TeaNekoInitializerScannerTest {
    /**
     * 验证优先级降序以及相同优先级下的稳定类名顺序。
     */
    @Test
    void shouldSortInitializersByPriorityAndClassName() {
        TeaNekoInitializerScanner scanner = new TeaNekoInitializerScanner(new TestBeanScanner());

        scanner.init();

        List<String> classNames = scanner.getInitializerDefinitions().stream()
                .map(definition -> definition.initializer().getClass().getName())
                .toList();
        assertEquals(List.of(
                RequiredHighPriorityInitializer.class.getName(),
                AlphaDefaultInitializer.class.getName(),
                BetaDefaultInitializer.class.getName()
        ), classNames);
    }

    /**
     * 验证初始化器默认可选，并保留必须初始化器声明。
     */
    @Test
    void shouldKeepRequiredMetadataAndOptionalDefault() {
        TeaNekoInitializerScanner scanner = new TeaNekoInitializerScanner(new TestBeanScanner());

        scanner.init();

        var initializerDefinitions = scanner.getInitializerDefinitions();
        assertTrue(initializerDefinitions.getFirst().metadata().required());
        assertFalse(initializerDefinitions.get(1).metadata().required());
        assertEquals(100, initializerDefinitions.getFirst().metadata().priority());
        assertEquals(0, initializerDefinitions.get(1).metadata().priority());
    }

    /**
     * 为扫描器测试提供固定的初始化器 Bean 集合。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class TestBeanScanner implements IBeanScanner {
        private final Map<String, Pair<TeaNekoInitializer, ITeaNekoInitializer>> initializers;

        private TestBeanScanner() {
            initializers = new LinkedHashMap<>();
            addInitializer("beta", new BetaDefaultInitializer());
            addInitializer("requiredHigh", new RequiredHighPriorityInitializer());
            addInitializer("alpha", new AlphaDefaultInitializer());
        }

        /**
         * 将测试初始化器及其类注解写入模拟扫描结果。
         *
         * @param name Bean 名称
         * @param initializer 初始化器实例
         */
        private void addInitializer(String name, ITeaNekoInitializer initializer) {
            initializers.put(
                    name,
                    IndependentPair.of(
                            initializer.getClass().getAnnotation(TeaNekoInitializer.class),
                            initializer
                    )
            );
        }

        /**
         * 返回测试 Bean 的实际类型。
         *
         * @param bean Bean 实例
         * @return Bean 实际类型
         */
        @Override
        public Class<?> getBeanClass(Object bean) {
            return bean.getClass();
        }

        /**
         * 返回符合指定接口的测试 Bean 类型。
         *
         * @param bean Bean 实例
         * @param beanInterface 期望接口
         * @param <T> 接口类型
         * @return Bean 实际类型
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> Class<? extends T> getBeanClass(Object bean, Class<T> beanInterface) {
            return (Class<? extends T>) bean.getClass();
        }

        /**
         * 当前测试不需要按接口查询 Bean。
         *
         * @param interfaceType 接口类型
         * @param <T> Bean 类型
         * @return 空映射
         */
        @Override
        public <T> Map<String, T> getBeansOfType(Class<T> interfaceType) {
            return Map.of();
        }

        /**
         * 当前测试不需要仅按注解查询 Bean。
         *
         * @param annotationType 注解类型
         * @param <A> 注解类型
         * @return 空映射
         */
        @Override
        public <A extends Annotation> Map<String, Pair<A, Object>> getBeansWithAnnotation(
                Class<A> annotationType) {
            return Map.of();
        }

        /**
         * 返回固定的初始化器定义集合。
         *
         * @param annotationType 初始化器注解类型
         * @param interfaceType 初始化器接口类型
         * @param <A> 注解类型
         * @param <T> 初始化器类型
         * @return 初始化器定义映射
         */
        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation, T> Map<String, Pair<A, T>>
        getBeansWithAnnotationAndInterface(Class<A> annotationType, Class<T> interfaceType) {
            return (Map<String, Pair<A, T>>) (Map<?, ?>) initializers;
        }
    }

    /**
     * 表示优先执行且失败时必须终止启动的测试初始化器。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @TeaNekoInitializer(required = true, priority = 100)
    private static final class RequiredHighPriorityInitializer implements ITeaNekoInitializer {
        /**
         * 测试初始化器无需创建资源。
         *
         * @param plugin 当前 Paper 插件
         */
        @Override
        public void initialize(JavaPlugin plugin) {
        }
    }

    /**
     * 表示类名排序靠前的默认优先级测试初始化器。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @TeaNekoInitializer
    private static final class AlphaDefaultInitializer implements ITeaNekoInitializer {
        /**
         * 测试初始化器无需创建资源。
         *
         * @param plugin 当前 Paper 插件
         */
        @Override
        public void initialize(JavaPlugin plugin) {
        }
    }

    /**
     * 表示类名排序靠后的默认优先级测试初始化器。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    @TeaNekoInitializer
    private static final class BetaDefaultInitializer implements ITeaNekoInitializer {
        /**
         * 测试初始化器无需创建资源。
         *
         * @param plugin 当前 Paper 插件
         */
        @Override
        public void initialize(JavaPlugin plugin) {
        }
    }
}
