package net.vonbrandis;

/**
* Created by joakim on 03.01.15.
*/
class Logger {
    public void debug(String msg, Object... params) {
        System.out.println(String.format(msg, params));
    }
}
