package ren.haom.pikeupcode;

public final class Parcel {
    public long id, receivedAt, pickedAt;
    public String code = "", company = "", station = "", address = "";
    public String tracking = "", phone = "", url = "", body = "", sender = "";

    public String place() {
        return !station.isEmpty() ? station : !address.isEmpty() ? address : "取件地点未提供";
    }
}
