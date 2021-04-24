/*
 * Minosoft
 * Copyright (C) 2020 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.protocol.network.socket;

import de.bixilon.minosoft.protocol.exceptions.PacketParseException;
import de.bixilon.minosoft.protocol.exceptions.PacketTooLongException;
import de.bixilon.minosoft.protocol.network.Network;
import de.bixilon.minosoft.protocol.network.connection.Connection;
import de.bixilon.minosoft.protocol.packets.c2s.C2SPacket;
import de.bixilon.minosoft.protocol.packets.c2s.login.EncryptionResponseC2SPacket;
import de.bixilon.minosoft.protocol.packets.s2c.S2CPacket;
import de.bixilon.minosoft.protocol.packets.s2c.login.PacketEncryptionRequest;
import de.bixilon.minosoft.protocol.protocol.ConnectionStates;
import de.bixilon.minosoft.protocol.protocol.CryptManager;
import de.bixilon.minosoft.protocol.protocol.PacketTypes;
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition;
import de.bixilon.minosoft.util.Pair;
import de.bixilon.minosoft.util.ServerAddress;
import de.bixilon.minosoft.util.logging.Log;
import de.bixilon.minosoft.util.logging.LogMessageType;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingSocketNetwork extends Network {
    private final LinkedBlockingQueue<C2SPacket> queue = new LinkedBlockingQueue<>();
    private Thread socketReceiveThread;
    private Thread socketSendThread;
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;

    public BlockingSocketNetwork(Connection connection) {
        super(connection);
    }

    private static int readStreamVarInt(InputStream inputStream) throws IOException {
        int readCount = 0;
        int varInt = 0;
        int currentByte;
        do {
            currentByte = inputStream.read();
            if (currentByte == -1) {
                throw new SocketException("Socket closed");
            }
            int value = (currentByte & 0x7F);
            varInt |= (value << (7 * readCount));

            readCount++;
            if (readCount > 5) {
                throw new RuntimeException("VarInt is too big");
            }
        } while ((currentByte & 0x80) != 0);
        return varInt;
    }

    @Override
    public void connect(ServerAddress address) {
        if (this.connection.isConnected() || this.connection.getConnectionState() == ConnectionStates.CONNECTING) {
            return;
        }
        this.connection.setLastException(null);
        this.connection.setConnectionState(ConnectionStates.CONNECTING);
        this.socketReceiveThread = new Thread(() -> {
            try {
                this.socket = new Socket();
                this.socket.setSoTimeout(ProtocolDefinition.SOCKET_CONNECT_TIMEOUT);
                this.socket.connect(new InetSocketAddress(address.getHostname(), address.getPort()), ProtocolDefinition.SOCKET_CONNECT_TIMEOUT);
                // connected, use minecraft timeout
                this.socket.setSoTimeout(ProtocolDefinition.SOCKET_TIMEOUT);
                this.connection.setConnectionState(ConnectionStates.HANDSHAKING);
                this.socket.setKeepAlive(true);
                this.outputStream = this.socket.getOutputStream();
                this.inputStream = this.socket.getInputStream();


                initSendThread();

                this.socketReceiveThread.setName(String.format("%d/SocketReceive", this.connection.getConnectionId()));


                while (this.connection.getConnectionState() != ConnectionStates.DISCONNECTING) {
                    if (!this.socket.isConnected() || this.socket.isClosed()) {
                        break;
                    }
                    try {
                        var typeAndPacket = prepareS2CPacket(this.inputStream);
                        handlePacket(typeAndPacket.getKey(), typeAndPacket.getValue());
                    } catch (PacketParseException e) {
                        Log.printException(e, LogMessageType.NETWORK_PACKETS_IN_ERROR);
                    }
                }
                this.connection.disconnect();
            } catch (Throwable exception) {
                // Could not connect
                this.connection.setConnectionState(ConnectionStates.DISCONNECTING);
                if (this.socketSendThread != null) {
                    this.socketSendThread.interrupt();
                }
                if (exception instanceof SocketException && exception.getMessage().equals("Socket closed")) {
                    this.connection.setConnectionState(ConnectionStates.DISCONNECTED);
                    return;
                }
                Log.printException(exception, LogMessageType.NETWORK_PACKETS_IN_ERROR);
                this.connection.setLastException(exception);
                this.connection.setConnectionState(ConnectionStates.FAILED);
            }
        }, String.format("%d/Socket", this.connection.getConnectionId()));
        this.socketReceiveThread.start();
    }

    @Override
    public void sendPacket(C2SPacket packet) {
        this.queue.add(packet);
    }

    @Override
    public void disconnect() {
        if (this.connection.isDisconnected()) {
            // already trying
            return;
        }
        this.connection.setConnectionState(ConnectionStates.DISCONNECTING);
        this.queue.clear();
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.socketReceiveThread.interrupt();
        this.socketSendThread.interrupt();
        this.connection.setConnectionState(ConnectionStates.DISCONNECTED);
    }

    @Override
    protected void handlePacket(PacketTypes.S2C packetType, S2CPacket packet) {
        super.handlePacket(packetType, packet);
        if (packet instanceof PacketEncryptionRequest) {
            try {
                Thread.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void initSendThread() {
        this.socketSendThread = new Thread(() -> {
            try {
                while (this.connection.getConnectionState() != ConnectionStates.DISCONNECTING) {
                    // wait for data or send until it should disconnect

                    // check if still connected
                    if (!this.socket.isConnected() || this.socket.isClosed()) {
                        break;
                    }

                    C2SPacket packet = this.queue.take();
                    packet.log();

                    this.outputStream.write(prepareC2SPacket(packet));
                    this.outputStream.flush();
                    if (packet instanceof EncryptionResponseC2SPacket packetEncryptionResponse) {
                        // enable encryption
                        enableEncryption(packetEncryptionResponse.getSecretKey());
                        // wake up other thread
                        this.socketReceiveThread.interrupt();
                    }
                }
            } catch (IOException | InterruptedException ignored) {
            }
        }, String.format("%d/SocketSend", this.connection.getConnectionId()));
        this.socketSendThread.start();
    }

    private Pair<PacketTypes.S2C, S2CPacket> prepareS2CPacket(InputStream inputStream) throws IOException, PacketParseException {
        int packetLength = readStreamVarInt(inputStream);

        if (packetLength > ProtocolDefinition.PROTOCOL_PACKET_MAX_SIZE) {
            inputStream.skip(packetLength);
            throw new PacketTooLongException(packetLength);
        }

        byte[] bytes = this.inputStream.readNBytes(packetLength);
        return super.receiveS2CPacket(bytes);
    }

    protected void enableEncryption(SecretKey secretKey) {
        this.inputStream = new CipherInputStream(this.inputStream, CryptManager.createNetCipherInstance(Cipher.DECRYPT_MODE, secretKey));
        this.outputStream = new CipherOutputStream(this.outputStream, CryptManager.createNetCipherInstance(Cipher.ENCRYPT_MODE, secretKey));
        Log.debug("Encryption enabled!");
    }
}
