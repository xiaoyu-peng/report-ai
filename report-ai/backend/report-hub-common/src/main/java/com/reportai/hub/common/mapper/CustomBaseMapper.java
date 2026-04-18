package com.reportai.hub.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface CustomBaseMapper<T> extends BaseMapper<T> {
    
    default IPage<T> selectPageByMap(Page<T> page, @Param("params") Map<String, Object> params) {
        return selectPage(page, null);
    }
    
    default List<T> selectListByMap(@Param("params") Map<String, Object> params) {
        return selectList(null);
    }
    
    default int logicalDeleteById(@Param("id") Long id) {
        return deleteById(id);
    }
    
    default int logicalDeleteBatchIds(@Param("ids") List<Long> ids) {
        return deleteBatchIds(ids);
    }
}
