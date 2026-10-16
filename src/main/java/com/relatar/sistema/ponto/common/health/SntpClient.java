package com.relatar.sistema.ponto.common.health;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Cliente SNTP minimo (RFC 4330) usado apenas para MEDIR a diferenca entre o relogio do servidor
 * e uma referencia NTP. A sincronizacao em si e responsabilidade do sistema operacional (chrony/systemd-timesyncd).
 *
 * <p>Formula: {@code offset = ((T2 - T1) + (T3 - T4)) / 2}, onde T1 = envio local, T2 = recebimento no servidor,
 * T3 = envio pelo servidor e T4 = recebimento local. Offset positivo significa relogio local atrasado.
 */
public class SntpClient {

    static final int PACKET_SIZE = 48;
    static final int NTP_PORT = 123;
    /** Segundos entre 1900-01-01 (epoca NTP) e 1970-01-01 (epoca Unix). */
    static final long NTP_TO_UNIX_SECONDS = 2_208_988_800L;
    private static final long TWO_POW_32 = 1L << 32;

    private static final int ORIGINATE_OFFSET = 24;
    private static final int RECEIVE_OFFSET = 32;
    private static final int TRANSMIT_OFFSET = 40;
    private static final int MODE_SERVER = 4;

    private final Clock clock;

    public SntpClient(Clock clock) {
        this.clock = clock;
    }

    public Duration queryOffset(String host, Duration timeout) throws IOException {
        byte[] request = new byte[PACKET_SIZE];
        request[0] = 0b00_100_011; // LI = 0, versao = 4, modo = 3 (cliente)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
            InetAddress address = InetAddress.getByName(host);
            Instant t1 = clock.instant();
            writeTimestamp(request, TRANSMIT_OFFSET, t1);
            socket.send(new DatagramPacket(request, request.length, address, NTP_PORT));

            DatagramPacket response = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
            socket.receive(response);
            Instant t4 = clock.instant();
            return computeOffset(request, Arrays.copyOf(response.getData(), response.getLength()), t1, t4);
        }
    }

    static Duration computeOffset(byte[] request, byte[] response, Instant t1, Instant t4) {
        if (response.length < PACKET_SIZE) {
            throw new IllegalStateException("Resposta NTP incompleta");
        }
        if ((response[0] & 0b111) != MODE_SERVER) {
            throw new IllegalStateException("Resposta NTP com modo inesperado");
        }
        int stratum = response[1] & 0xFF;
        if (stratum < 1 || stratum > 15) {
            // Stratum 0 indica "kiss-o'-death" (servidor recusou atender); acima de 15, nao sincronizado.
            throw new IllegalStateException("Servidor NTP nao sincronizado ou recusou a consulta (stratum " + stratum + ")");
        }
        if (!Arrays.equals(request, TRANSMIT_OFFSET, TRANSMIT_OFFSET + 8,
                response, ORIGINATE_OFFSET, ORIGINATE_OFFSET + 8)) {
            throw new IllegalStateException("Resposta NTP nao corresponde a requisicao enviada");
        }
        Instant t2 = readTimestamp(response, RECEIVE_OFFSET);
        Instant t3 = readTimestamp(response, TRANSMIT_OFFSET);
        long offsetNanos = (Duration.between(t1, t2).toNanos() + Duration.between(t4, t3).toNanos()) / 2;
        return Duration.ofNanos(offsetNanos);
    }

    static Instant readTimestamp(byte[] buffer, int offset) {
        long seconds = readUnsignedInt(buffer, offset);
        long fraction = readUnsignedInt(buffer, offset + 4);
        // RFC 4330: com o bit mais significativo zerado, o instante pertence a era 1 (a partir de 2036-02-07).
        if ((seconds & 0x8000_0000L) == 0) {
            seconds += TWO_POW_32;
        }
        long nanos = (fraction * 1_000_000_000L) >>> 32;
        return Instant.ofEpochSecond(seconds - NTP_TO_UNIX_SECONDS, nanos);
    }

    static void writeTimestamp(byte[] buffer, int offset, Instant instant) {
        long seconds = (instant.getEpochSecond() + NTP_TO_UNIX_SECONDS) % TWO_POW_32;
        long fraction = ((long) instant.getNano() << 32) / 1_000_000_000L;
        writeUnsignedInt(buffer, offset, seconds);
        writeUnsignedInt(buffer, offset + 4, fraction);
    }

    private static long readUnsignedInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFFL) << 24)
                | ((buffer[offset + 1] & 0xFFL) << 16)
                | ((buffer[offset + 2] & 0xFFL) << 8)
                | (buffer[offset + 3] & 0xFFL);
    }

    private static void writeUnsignedInt(byte[] buffer, int offset, long value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }
}
