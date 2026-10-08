package org.linbaogu.romhub.hc.provision;

import org.linbaogu.romhub.hc.provision.IAnimCallback;

interface IProvisionAnim {

    boolean isAnimEnd();

    void playBackAnim(int animY);
    void playNextAnim(int i);

    void registerRemoteCallback(IAnimCallback callback);
    void unregisterRemoteCallback(IAnimCallback callback);
}
