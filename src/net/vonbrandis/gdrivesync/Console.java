package net.vonbrandis.gdrivesync;

public class Console {

    private boolean debug = false;
    private boolean transactions = false;
    private boolean folderSummary = false;
    private boolean totalSummary = true;

    public Console() {
    }

    public void newline() {
        System.out.println();
    }

    public void transaction(String msg, Object... params) {
        if (!transactions) return;
        System.out.println(String.format(msg, params));
    }

    public void folderSummary(String msg, Object... params) {
        if (!folderSummary) return;
        System.out.println(String.format(msg, params));
    }

    public void totalSummary(String msg, Object... params) {
        if (!totalSummary) return;
        System.out.println(String.format(msg, params));
    }

    public void log(String msg, Object... params) {
        System.out.println(String.format(msg, params));
    }

    public void debug(String msg, Object... params) {
        if (!debug) return;
        System.out.println(String.format(msg, params));
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setTransactions(boolean transactions) {
        this.transactions = transactions;
    }

    public void setFolderSummary(boolean folderSummary) {
        this.folderSummary = folderSummary;
    }

    public void setTotalSummary(boolean totalSummary) {
        this.totalSummary = totalSummary;
    }
}
