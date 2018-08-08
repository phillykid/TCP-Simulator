
/**
 * A class which represents the receiver's application. It simply prints out the message received from the tranport layer.
 */
public class ReceiverApplication
{
    public void receiveMessage(Message msg)
    {
        String print_template = 
        "\n\n--------------------------------------------------------------------- START \n"+
        "Message transport from Transport Layer to Receiver Application Successful: \n\n" +
        msg.getMessage() +
        "\n\n---------------------------------------------------------------------  END ";

        System.out.println(print_template);
        
    }

}
