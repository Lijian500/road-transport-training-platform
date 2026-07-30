package me.lj.train.common.core.page;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回。
 *
 * @param <T> 列表项类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = Collections.emptyList();
    private long total;
    private int pageNumber;
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int pageNumber, int pageSize) {
        this.records = records == null ? Collections.<T>emptyList() : records;
        this.total = total;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, long total, PageRequest request) {
        return new PageResult<T>(records, total, request.getPageNumber(), request.getPageSize());
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
