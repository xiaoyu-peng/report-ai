package com.reportai.hub.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报告质量体检结果（覆盖度仪表盘 + 事实性可疑列表的数据源）。 */
@Data
@TableName("report_quality")
public class ReportQuality {
    @TableId(type = IdType.NONE)
    private Long reportId;
    /** 引用覆盖率 0~100：含 [n] 角标的段落数 / 总段落数 * 100 */
    private BigDecimal coverageRate;
    private Integer citationsTotal;
    private Integer paragraphsTotal;
    private Integer paragraphsCited;
    /** {"行业报告":12,"政策法规":4,"媒体资讯":3} */
    private String kbDistribution;
    /** [{text,reason,severity:warn|info,suggestion:mark|fix|soften}] */
    private String suspiciousFacts;
    private LocalDateTime checkedAt;
}
