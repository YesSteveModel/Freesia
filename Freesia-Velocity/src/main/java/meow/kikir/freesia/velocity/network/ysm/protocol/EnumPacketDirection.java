package meow.kikir.freesia.velocity.network.ysm.protocol;

public enum EnumPacketDirection {
    C2S("server_bound"),
    S2C("client_bound");

    private final String name;

    EnumPacketDirection(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
