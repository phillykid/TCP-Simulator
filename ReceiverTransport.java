import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


/**
 * A class which represents the receiver transport layer
 */
public class ReceiverTransport
{
    private ReceiverApplication ra;
    private NetworkLayer nl;
    private boolean bufferingPackets; //Whether we accept packets out of order.
    private int lowest_sequential_received; //Last seq we got an ack for
    private int curr_pkt_seq; //Seq number of last sent packet
    private ArrayList<Packet> buffer = new ArrayList<Packet>();
    


    public ReceiverTransport(NetworkLayer nl){
        ra = new ReceiverApplication();
        this.nl=nl;
        initialize();
    }

    public void initialize()
    {
        this.lowest_sequential_received=0;
        this.curr_pkt_seq=0;
        this.bufferingPackets = false;
    }

    /**
     * Receive packet from the sender side
     * Check if the packet is corrupt
     * Check if the packet received is in-order:
     *  if in order, send ack and move window send corresponding package up to the application level
     *  otherwise, send same ack as before and buffer the received package (if buffer is on)
     */
    public void receiveMessage(Packet pkt)
    {

        if(pkt.isCorrupt())
        {
            System.out.println("\n----- Corrupt Packet Received from Sender! -----\n");
            sendACK(lowest_sequential_received);
            return;
        }



        curr_pkt_seq = pkt.getSeqnum();

            if(lowest_sequential_received == curr_pkt_seq) {
                System.out.println("\n----- Received Packet From Sender-----\n");
                

                buffer.add(pkt);
                sortPacket();

                lowest_sequential_received = cumulative_ack();
                sendACK(lowest_sequential_received);
                clear_buffer_of_completed();

            }
            else if(bufferingPackets && pkt.getSeqnum()>lowest_sequential_received) {

                sendACK(lowest_sequential_received);
                buffer.add(pkt);
    }
            else{
                sendACK(lowest_sequential_received);

            }
    }


    /**
     * In case out-of-order package is received and the receiver base packet just arrived,
     * send accumulative ack
     */
    private int cumulative_ack() {
        int highest_ack = buffer.get(0).getAcknum();
        for (int i = 0; i < buffer.size() - 1; i++) {
            if (buffer.get(i).getAcknum() == buffer.get(i + 1).getSeqnum()) {
                highest_ack = buffer.get(i + 1).getAcknum();
            }
                else{
                return highest_ack;
            }

            }
    return highest_ack;
    }


    /**
     * Comparator used to sort packets in order of sequence number
     */
    class PacketPriority implements Comparator<Packet>{
        public int compare(Packet p1, Packet p2){
            return Integer.compare(p1.getSeqnum(), p2.getSeqnum());
        }
    }

    /**
     * Rearrange packets in order of sequence number
     */
    public void sortPacket(){

        PacketPriority pkt_compare =new PacketPriority();
        Collections.sort(buffer, pkt_compare);

    }



    /**
     * Remove packets that are successfully sent up to the application level from the buffer
     */
    private void clear_buffer_of_completed()
    {

        ArrayList<Packet> segmented_packets = new ArrayList<Packet>();
        for(int i=0; i<buffer.size(); i++)
        {
            if(buffer.get(i).isSplit()==false){
                ra.receiveMessage(buffer.get(i).getMessage());
                buffer.remove(i);
            }
            else{
                segmented_packets.add(buffer.get(i));
            }
        }
        work_through_segments(segmented_packets);

        while(check_for_more_completed_segments(buffer)) {
            clear_buffer_of_completed();

        }


    }

    private boolean check_for_more_completed_segments(ArrayList<Packet> segmented_packets) {
        for (int i = 0; i < segmented_packets.size() - 1; i++) {
            if (segmented_packets.get(i).getAcknum() == segmented_packets.get(i + 1).getSeqnum()) {
                if (segmented_packets.get(i + 1).islast_Shard() == true) {
                    return true;
                }
            }
            return false;

        }
        return false;
    }
    /**
     * Looks for full segments of split packets to send to combine_split_packets
     */
    private void work_through_segments(ArrayList<Packet> segmented_packets)
    {

        int counter=0;

        for(int i=0; i<segmented_packets.size()-1; i++)
        {
            if(segmented_packets.get(i).getAcknum()==segmented_packets.get(i+1).getSeqnum()) {
                counter++;
                if(segmented_packets.get(i+1).islast_Shard()==true)
                {
                    counter++;
                    ArrayList<Packet> pkts_to_combine = new ArrayList<Packet>();
                    for(int j=0; j<counter; j++) {
                        pkts_to_combine.add(segmented_packets.get(j));
                    }

                    ra.receiveMessage(new Message(combine_split_packets(pkts_to_combine)));
                    buffer.removeAll(pkts_to_combine);
                    break;


                }
            }

        }


    }


    /**
     * Re-stitching segments into one packet to send it up to the application level
     */
    private String combine_split_packets(ArrayList<Packet> pkts_to_combine)
    {
        String combined_msg="";
        for (Packet pkt:pkts_to_combine) {
            combined_msg+=pkt.getMessage().getMessage();

        }
        return combined_msg;
    }

    private void sendACK(int number)
    {
        Packet toSend = new Packet(new Message("ACK"),0,number,0);
        nl.sendPacket(toSend,Event.SENDER);
    }



    public void setProtocol(int n)
    {
        if(n>0)
            bufferingPackets=true;
        else
            bufferingPackets=false;
    }

}
