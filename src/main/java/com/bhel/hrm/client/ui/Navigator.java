package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.Session;

/** How a screen asks the window to show another screen. Kept apart from the window so screens can be tested alone. */
public interface Navigator {

    /** The login screen. {@code message} (may be {@code null}) is shown on it, for example "session expired". */
    void showLogin(String message);

    /** For someone who must choose their own password before doing anything else. */
    void showChangePassword(Session session);

    void showHome(Session session);
}
