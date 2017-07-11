package org.wso2.extensions.siddhi.io.file.messageProcessors;

import org.wso2.carbon.messaging.BinaryCarbonMessage;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.CarbonMessageProcessor;
import org.wso2.carbon.messaging.ClientConnector;
import org.wso2.carbon.messaging.TransportSender;
import org.wso2.extensions.siddhi.io.file.util.Constants;
import org.wso2.extensions.siddhi.io.file.util.FileSourceConfiguration;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class  FileProcessor implements CarbonMessageProcessor {
    private SourceEventListener sourceEventListener;
    private FileSourceConfiguration fileSourceConfiguration;
    private String mode;
    private Pattern pattern;
    private int readBytes;
    private StringBuilder sb = new StringBuilder();

    public FileProcessor(SourceEventListener sourceEventListener, FileSourceConfiguration fileSourceConfiguration) {
        this.sourceEventListener = sourceEventListener;
        this.fileSourceConfiguration = fileSourceConfiguration;
        this.mode = fileSourceConfiguration.getMode();
        configureFileMessageProcessor();
    }

    @Override
    public boolean receive(CarbonMessage carbonMessage, CarbonCallback carbonCallback) throws Exception {
        byte[] content = ((BinaryCarbonMessage) carbonMessage).readBytes().array();
        String msg = new String(content);

        if (Constants.TEXT_FULL.equalsIgnoreCase(mode)) {
            if(msg != null && msg.length() > 0)
            sourceEventListener.onEvent(new String(content), null);
        } else if (Constants.BINARY_FULL.equalsIgnoreCase(mode)) {
            //TODO : implement consuming binary files (file processor)
            if(msg != null && msg.length() > 0)
                sourceEventListener.onEvent(content, null);
            carbonCallback.done(carbonMessage);;
        } else if (Constants.LINE.equalsIgnoreCase(mode)) {
            if(!fileSourceConfiguration.isTailingEnabled()) {
                InputStream is = new ByteArrayInputStream(content);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
                String line;
                while((line = bufferedReader.readLine()) != null){
                    if(line != null && line.length() > 0) {
                        readBytes = line.length();
                        sourceEventListener.onEvent(line.trim(), null);
                    }
                }
            } else {
                if(msg != null && msg.length() > 0) {
                    readBytes = msg.getBytes().length;
                    fileSourceConfiguration.updateFilePointer(readBytes);
                    sourceEventListener.onEvent(msg, null);
                }
            }
        } else if(Constants.REGEX.equalsIgnoreCase(mode)){
            int  lastMatchIndex = 0;
            if(!fileSourceConfiguration.isTailingEnabled()) {
                char[] buf = new char[10];
                InputStream is = new ByteArrayInputStream(content);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));

                while (bufferedReader.read(buf) != -1) {
                    lastMatchIndex = 0;
                    sb.append(new String(buf));
                    Matcher matcher = pattern.matcher(sb.toString().trim());
                    while (matcher.find()) {
                        String event = matcher.group(0);
                        lastMatchIndex = matcher.end();
                        sourceEventListener.onEvent(event, null);
                    }
                    String tmp;
                    tmp = sb.substring(lastMatchIndex);

                    sb.setLength(0);
                    sb.append(tmp);
                }
            }else{
                readBytes += content.length;
                fileSourceConfiguration.updateFilePointer(readBytes);

                sb.append(new String(content));
                Matcher matcher = pattern.matcher(sb.toString().trim());
                while (matcher.find()) {
                    String event = matcher.group(0);
                    lastMatchIndex = matcher.end();
                    sourceEventListener.onEvent(event, null);
                }
                String tmp;
                tmp = sb.substring(lastMatchIndex);

                sb.setLength(0);
                sb.append(tmp);
            }
        }
        return false;
    }

    @Override
    public void setTransportSender(TransportSender transportSender) {

    }

    @Override
    public void setClientConnector(ClientConnector clientConnector) {

    }

    @Override
    public String getId() {
        return null;
    }
    

    private void configureFileMessageProcessor() {
        String beginRegex = fileSourceConfiguration.getBeginRegex();
        String endRegex = fileSourceConfiguration.getEndRegex();
        if (beginRegex != null && endRegex != null) {
            pattern = Pattern.compile(beginRegex + "(.+?)" + endRegex);
        } else if (beginRegex != null && endRegex == null) {
            pattern = Pattern.compile(beginRegex + "(.+?)" + beginRegex);
        } else if (beginRegex == null && endRegex != null) {
            pattern = Pattern.compile(".+?" + endRegex);
        }
    }
}