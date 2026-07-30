package me.lj.train.common.core.page;

import java.io.Serializable;

/**
 * 统一分页请求。
 */
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_PAGE_SIZE = 100;

    private int pageNumber = 1;
    private int pageSize = 10;

    public PageRequest() {
    }

    public PageRequest(int pageNumber, int pageSize) {
        setPageNumber(pageNumber);
        setPageSize(pageSize);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = Math.max(pageNumber, 1);
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    public long getOffset() {
        return (long) (pageNumber - 1) * pageSize;
    }
}
