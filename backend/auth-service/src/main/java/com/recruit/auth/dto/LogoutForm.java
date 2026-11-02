package com.recruit.auth.dto;

import lombok.Data;

/**
 * 登出入参（A-04）。
 *
 * <p>body 可省略；缺省只登出当前设备。</p>
 */
@Data
public class LogoutForm {

    /** true：递增 token_version 并撤销该用户全部 Refresh 会话。 */
    private Boolean allDevices = Boolean.FALSE;
}
