import java.io.IOException;

public interface FT {
  
  /**
   * Client: request to send a whole file
   *
   * @return true if file exists and can be sent
   * @throws IOException
   */
  boolean file_req() throws IOException;
  
  /**
   * Server: request to receive a whole file (using an ARQ-protocol)
   *
   * @return true if file received correctly
   * @throws IOException
   */
  boolean file_init() throws IOException;
}
