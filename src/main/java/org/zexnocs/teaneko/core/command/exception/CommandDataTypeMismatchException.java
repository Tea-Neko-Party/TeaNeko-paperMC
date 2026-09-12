package org.zexnocs.teaneko.core.command.exception;

import org.zexnocs.teaneko.core.command.CommandData;
import org.zexnocs.teaneko.core.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teaneko.core.command.interfaces.ICommandDispatcher;

/**
 * 如果 {@link CommandData} 不符合 {@code Method} 参数的要求，则直接抛出该异常，表示无法匹配。
 * <p>这属于经常发生的异常，不需要 report，但是需要处理。
 *
 * @see ICommandDispatcher
 * @see ICommandArgumentProcessor
 * @author zExNocs
 * @date 2026/02/19
 * @since 4.0.0
 */
public class CommandDataTypeMismatchException extends Exception {
    public CommandDataTypeMismatchException() {
        super();
    }
}
