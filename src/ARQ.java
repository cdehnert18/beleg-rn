public interface ARQ {

  /**
   * Sender: Request the transmission of a data packet
   *
   * @param hlData data packet from higher layer
   * @param hlSize size of the data packet
   * @return true implies a correct ACK is received
   */
  boolean data_req(byte[] hlData, int hlSize, boolean lastTransmission);

  /**
   * Receiver/Server: Request to receive a data packet (first packet is start)
   * @param values Optional data for the Sender/Client
   * @return DataPacket without SW-Header
   * @throws TimeoutException Timeout of Socket
   */
  byte[] data_ind_req(int... values) throws TimeoutException;

  /**
   *
   * @return optional data from receiver
   */
  int getBackData();

  /** Receiver waits for a repeated last packet and send ACK again */
  void closeConnection();

  /**
   *
   * @return Statistics
   */
  int getRetransmissionCounter();

  int getRetryCounterStat();

  int getMTU();

  void setMTU(int MTU);
}
