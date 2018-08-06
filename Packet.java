import java.util.*;

/**
 * A class which represents a packet
 */
public class Packet
{
    
    private Message msg; //the enclosed message
    private int seqnum; //packets seq. number
    private int acknum; //packet ack. number
    private boolean split;
    private boolean last_chunck;
    private int checksum; //packet checksum


    Random ran; //random number generator

    public Packet(Message msg, int seqnum, int acknum, int checksum)
    {
        this.msg=msg;
        this.seqnum=seqnum;
        this.acknum=acknum;
        this.checksum=checksum;
        this.ran=new Random();
        split=false;
        last_chunck=true;
        setChecksum();
    }
    
    public Packet(Packet other)
    {
        this.msg=new Message(new String(other.msg.getMessage()));
        this.seqnum=other.seqnum;
        this.acknum=other.acknum;
        this.checksum=other.checksum;
        this.ran=other.ran;
        this.split=other.split;
        this.last_chunck=other.last_chunck;
        setChecksum();



    }

    public int getAcknum()
    {
        return acknum;
    }
    
    public int getSeqnum()
    {
        return seqnum;
    }

    public boolean isSplit(){return  split;}

    public boolean isLast_chunck(){return last_chunck;}

    public void setSplit(boolean split){this.split =  split;}

    public void setLast(boolean last){this.last_chunck =last_chunck;}

    public Message getMessage()
    {
        return msg;
    }
    
    public void setChecksum()
    {
        checksum = calculate_checksum();
    }

    /**
     * Check if the segment is corrupt by checksum
     * Checks all ack number, sequence number, and the message(also add checksum to itself)
     */
    private int calculate_checksum()
    {
        //converting mesage to bytes
        char[] msgChar = msg.getMessage().toCharArray();
        byte[] result = new byte[msgChar.length];
        int checksum_builder=0;


        for(int i = 0; i < msgChar.length; i++) {
            result[i] = (byte) msgChar[i];
        }

            for(byte b : result){
                checksum_builder+=b;
            }

            checksum_builder+= acknum;
            checksum_builder+= seqnum;
            checksum_builder+=checksum_builder;
            checksum_builder=~checksum_builder;
           // System.out.println("CheckSum: "+checksum_builder);

            return checksum_builder;




        }

    
    public boolean isCorrupt()
    {


        return checksum!=calculate_checksum();
    }
    
    /**
     * This method corrupts the packet the follwing way:
     * corrupt the message with a 75% chance
     * corrupt the seqnum with 12.5% chance
     * corrupt the ackum with 12.5% chance
     */
    public void corrupt()
    {
        double num =ran.nextDouble();
        
        if(num<0.75)
        {this.msg.corruptMessage();}
        else if(num<0.875)
        {this.seqnum=this.seqnum+1;}
        else
        {this.acknum=this.acknum+1;}

    }
    

}
