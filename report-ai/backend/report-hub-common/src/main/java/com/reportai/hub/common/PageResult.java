package com.reportai.hub.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装类
 *
 * @param <T> 数据类型
 * @author skill-hub
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private Long total;
    private Long size;
    private Long current;
    private Long pages;

    public static <T> PageResult<T> of(List<T> records, Long total, Long size, Long current) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setSize(size);
        result.setCurrent(current);
        if (size != null && size > 0) {
            result.setPages((total + size - 1) / size);
        }
        return result;
    }
}
