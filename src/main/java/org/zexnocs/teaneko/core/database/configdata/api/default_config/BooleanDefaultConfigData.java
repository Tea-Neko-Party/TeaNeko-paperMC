package org.zexnocs.teaneko.core.database.configdata.api.default_config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teaneko.core.database.configdata.api.IConfigData;
import org.zexnocs.teaneko.core.framework.description.Description;

/**
 * 布尔值默认配置数据类，表示一个布尔类型的配置项数据。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BooleanDefaultConfigData implements IConfigData {
    @Description("布尔值")
    private boolean value = false; // 默认为 false
}
