package org.zexnocs.teanekocore.reload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证扫描器首次初始化失败后的重试行为。
 *
 * @author zExNocs
 * @date 2026/09/12
 * @since paperMC-1.0.0alpha
 */
class AbstractScannerTest {
    /**
     * 验证首次扫描失败不会永久锁定初始化标记。
     */
    @Test
    void shouldAllowRetryAfterInitialScanFailure() {
        RetriableScanner scanner = new RetriableScanner();

        assertThrows(IllegalStateException.class, scanner::init);
        assertDoesNotThrow(scanner::init);
        scanner.init();

        assertEquals(2, scanner.scanCount);
    }

    /**
     * 首次扫描失败、第二次扫描成功的测试扫描器。
     *
     * @author zExNocs
     * @date 2026/09/12
     * @since paperMC-1.0.0alpha
     */
    private static final class RetriableScanner extends AbstractScanner {
        private int scanCount;

        /**
         * 首次调用时模拟扫描失败。
         */
        @Override
        protected void _scan() {
            scanCount++;
            if (scanCount == 1) {
                throw new IllegalStateException("模拟首次扫描失败。");
            }
        }

        /**
         * 当前测试没有需要清理的扫描结果。
         */
        @Override
        protected void _clear() {
        }
    }
}
