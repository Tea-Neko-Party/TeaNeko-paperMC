package org.zexnocs.teaneko.core.command.easydata;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teaneko.core.database.easydata.BaseEasyDataObject;

/**
 * 用于处理命令相关的数据对象的类。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@NoArgsConstructor
@Entity
@Table(name = "command_data", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
})
public class CommandEasyDataObject extends BaseEasyDataObject {
    protected CommandEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
