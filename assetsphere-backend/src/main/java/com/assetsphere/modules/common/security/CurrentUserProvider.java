package com.assetsphere.modules.common.security;

public interface CurrentUserProvider {
    CurrentUser requireCurrentUser();
}
