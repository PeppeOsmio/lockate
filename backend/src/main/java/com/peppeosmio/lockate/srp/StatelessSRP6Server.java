package com.peppeosmio.lockate.srp;

import org.bouncycastle.crypto.agreement.srp.SRP6Server;

import java.math.BigInteger;

public class StatelessSRP6Server extends SRP6Server {

    public void setInitialState(BigInteger b, BigInteger B) {
        this.b = b;
        this.B = B;
    }

    public BigInteger getb() {
        return this.b;
    }
}
