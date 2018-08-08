import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
/**
 * A class which represents the sender transport layer
 */
public class SenderTransport
{
    private NetworkLayer nl;
    private Timeline tl;

    private int time_to_wait;
    private int seqNum;
    private int waiting_on; //ack that we are waiting to receive
    private int last_sent; //seq number of the packet last sent to receiver
    private int repeat_ack_counter; //count duplicate ack
    private int window_size; //window size
    private int mss; //maximum segment size
    private boolean bufferingPackets; //buffer on/off
    private ArrayList buffer = new ArrayList<Message>();
    private ArrayList<Packet> working_window = new ArrayList<Packet>();
    private Queue<Packet> packets_to_send = new LinkedList<>();

    public SenderTransport(NetworkLayer nl){
        this.nl=nl;
        initialize();

    }



    public void initialize()
    {
        seqNum=0;
        waiting_on=0;
        last_sent=0;
        window_size=0;
        repeat_ack_counter=0;
        time_to_wait=200; //timer value
    }

    /**
     * Get message from Application layer
     * Break message into mss if needed
     */
    public void sendMessage(Message msg)
    {
        if(msg.getMessage().getBytes().length>mss && mss>0)
        {

            ArrayList<String> make_packets_of = split_to_mss(msg);
            for(int i=0; i<make_packets_of.size(); i++) {
                if(i==make_packets_of.size()-1){
                    negotiate_packet_message(new Message(make_packets_of.get(i)),true,false);
                }
                else{
                    negotiate_packet_message(new Message(make_packets_of.get(i)),true,true);
                }
            }

        }
        else{
            negotiate_packet_message(msg,false,false);
        }


    }

    /**
     * Split message into smaller chunk to fit into mss
     */
    private ArrayList<String> split_to_mss(Message msg){
        ArrayList<String> split_msg = new ArrayList<String>();
        byte[] split_bytes = msg.getMessage().getBytes();
        ArrayList<Byte> string_constructor = new ArrayList<>();
        for(int i=0; i<split_bytes.length; i++)
        {
        if(string_constructor.size()<mss){
            string_constructor.add(split_bytes[i]);
        }
        else{
            Byte [] intermidate_bytes = string_constructor.toArray(new Byte[string_constructor.size()]);

            byte [] bytes_for_string = new byte[intermidate_bytes.length];
            int counter = 0;
            for(Byte b: intermidate_bytes)
                bytes_for_string[counter++] = b.byteValue();
            split_msg.add(new String(bytes_for_string));
            string_constructor.clear();
            i--;
        }


        }
        Byte [] intermidate_bytes = string_constructor.toArray(new Byte[string_constructor.size()]);

        byte [] bytes_for_string = new byte[intermidate_bytes.length];
        int counter = 0;
        for(Byte b: intermidate_bytes)
            bytes_for_string[counter++] = b.byteValue();
        split_msg.add(new String(bytes_for_string));
        return split_msg;
    }

    /**
     * Check recieved data to create proper packet for transmission
     * Works around the working_window to send data - if full leaves in buffer
     */
    private void negotiate_packet_message(Message msg, boolean split, boolean last)
    {
        int ackNumber = seqNum+msg.getMessage().getBytes().length+1;
        Packet toSend = new Packet(msg,seqNum,ackNumber,0);
        if(split==true)
        {

            toSend.setSplit(true);
            if(last==true) toSend.setLast(true);
        }
        packets_to_send.add(toSend);

        seqNum=ackNumber;
        if(working_window.isEmpty()){
            working_window.add(packets_to_send.remove());
            work_through_window();
        }
        else if(working_window.size()<window_size) {
            send_new_packet_working_window();
        }
    }

    /**
     * Go through the working_window and send packets.
     */
    public void work_through_window() {
        for (int i = 0; i < working_window.size(); i++) {
            nl.sendPacket(new Packet(working_window.get(i)), Event.RECEIVER);


            if (tl.isTimerOn()==0) {
                tl.startTimer(time_to_wait);
                waiting_on = working_window.get(i).getAcknum();

            }
            last_sent = seqNum;

        }
    }
    /**
     * In case where ack for in-order packet is arrived or one packet at a time needs to added,
     * remove that packet from the buffer and add new packet at the end of the window
     */
    public void send_new_packet_working_window()
{
    working_window.add(packets_to_send.remove());
    nl.sendPacket(new Packet(working_window.get(working_window.size()-1)), Event.RECEIVER);

    if(tl.isTimerOn() == 0) {
        tl.startTimer(time_to_wait);
        waiting_on = working_window.get(working_window.size()-1).getAcknum();

    }
    last_sent = seqNum;

    seqNum=working_window.get(working_window.size()-1).getAcknum();
}

    /**
     * Resend the sender base (first packet of the window) to the receiver
     */
private void re_send_first_packet_in_window()
{
    nl.sendPacket(new Packet(working_window.get(0)), Event.RECEIVER);
    tl.stopTimer();
    tl.startTimer(time_to_wait);
    waiting_on = working_window.get(0).getAcknum();
    last_sent = seqNum;

    

}


    public void receiveMessage(Packet pkt)
    {

        if(pkt.isCorrupt()) {
            return;
        }
        if(!working_window.isEmpty()) {
            if (working_window.get(0).getAcknum() == pkt.getAcknum()) {
                System.out.println("GOT CORRECT ACK NUMBER FROM REC SIDE---------");
                tl.stopTimer();
                repeat_ack_counter = 0;
                working_window.remove(0);
                if (!packets_to_send.isEmpty()) working_window.add(packets_to_send.remove());
                if (!working_window.isEmpty()) {
                    tl.startTimer(time_to_wait);
                    waiting_on = working_window.get(0).getAcknum();
                }

            } else if (working_window.get(0).getSeqnum() == pkt.getAcknum()) {

                repeat_ack_counter++;
                if (repeat_ack_counter == 3) {
                    if (!bufferingPackets) {
                        work_through_window();
                        repeat_ack_counter = 0;
                    } else {
                        re_send_first_packet_in_window();
                        repeat_ack_counter = 0;
                        ;
                    }
                }
            } else if (working_window.get(0).getSeqnum() < pkt.getAcknum()) {
                tl.stopTimer();
                repeat_ack_counter = 0;
                cumulative_delivery_clear(pkt.getAcknum());
                while (working_window.size() < window_size - 1 && !packets_to_send.isEmpty()) {
                    working_window.add(packets_to_send.remove());

                }
                if (!working_window.isEmpty()) {

                    work_through_window();
                }
            }
        }

    }


    /**
     * Move window
     * clear out packets in the window to the packets with the largest ack
     */
    private void cumulative_delivery_clear(int largest_ack)
    {

        ArrayList<Packet> clone = new ArrayList<Packet>(working_window);
        for (Packet p:clone) {
            if(p.getSeqnum()<largest_ack)
            {
                working_window.remove(p);
            }

        }
    }

    /**
     * Timer expired
     * Resend the window or first packet in window depending on tcp
     */
    public void timerExpired()
    {
        if(!bufferingPackets) work_through_window();
        if(bufferingPackets) re_send_first_packet_in_window();
        System.out.println("The timer has expired!");
    }



    public void setTimeLine(Timeline tl)
    {
        this.tl=tl;
    }

    public void setWindowSize(int window_size)
    {
        this.window_size=window_size;
    }
    
    public void setMSS(int mss)
    {
        this.mss=mss;
    }
    

    public void setProtocol(int protocol)
    {
        if(protocol>0)
            bufferingPackets=true;
        else
            bufferingPackets=false;
    }

}
