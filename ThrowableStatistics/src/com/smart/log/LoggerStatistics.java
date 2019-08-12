package com.smart.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

public class LoggerStatistics {
    
   

    private static final String CONFIG_DIR = "config/config.properties";
    private static String LOG_DIR = "config/logs";
    private static String FILE_DIR = "config/file/";
    private static final String TARGET_FILE = "_out.xls";
    private static final String ENDS_WITH = ".*log.*";
    private static final List<String> excluded = new ArrayList<>();
    private static final List<String> included = new ArrayList<>();
    static{
        excluded.add("IllegalArgumentException");
        excluded.add("SFSExtensionException");
        excluded.add("SessionReconnectionException");
        excluded.add("EOFException");
        excluded.add("SFSRuntimeException");
        excluded.add("SFSJoinRoomException");
        excluded.add("SFSLoginException");
        excluded.add("TimeoutException");
        excluded.add("ServletException");
        excluded.add("SSLException");
        excluded.add("AEADBadTagException");
        
        included.add("NullPointerException");
    }
    private static final String JAVA_LANG = "java.lang.";
    private static final String ZONE_INDEX = "{ Zone: Zona";
    private static final int upRead = 5;
    private static final int downRead = 20;
    private static final String separate = "::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::";
    private static final String separats = "=//=//=//=";
    private static List<File> files;
    
    private static List<File> getFiles(String path) {
        File root = new File(path);
        List<File> files = new ArrayList<File>();
        if(!root.isDirectory()){
            files.add(root);
        }else{
            File[] subFiles = root.listFiles();
            for (File f : subFiles) {
                files.addAll(getFiles(f.getAbsolutePath()));
            }
        }
        return files;
    }
    public static final String SEPARATOR = System.getProperty("line.separator");
    
    private static final String DISCON = "User disconnected:";
    
    private static String name;
    
   

    public static void analysisLog() throws UnsupportedEncodingException, FileNotFoundException, IOException
    {
        HSSFWorkbook mwokrbook = new HSSFWorkbook();
        HSSFSheet msheet = mwokrbook.createSheet();
        List lists = null;
        String temp = null;
        
        List<String> exceptionStr = new ArrayList<>();
        List<String> zoneStr = new ArrayList<>();
        List<String> otherStr = new ArrayList<>();
        List<String> itemStr = new ArrayList<>();
        
        String[] s = new  String[3];
        List slist = new ArrayList<>();
        Pattern namePattern = Pattern.compile(ENDS_WITH);
        Map<String,String> map = new HashMap<String,String>();
        Map<String,Integer> mapCount = new HashMap<String,Integer>();
        int n = 0;
        for (File f : files) {
            name = f.getName();
            if(f.isFile() && namePattern.matcher(name).find()){
                InputStreamReader read = new InputStreamReader(new FileInputStream(f),"utf-8");
                BufferedReader reader = new BufferedReader(read);
                int iLine = 0,firstLine = 0,endLine = 0;
                Boolean reRead = false;
                Boolean exceptionFind = false;
                Boolean zoneFind = false;
                Boolean separateFind = false;
                while((temp=reader.readLine())!=null){
                    iLine ++;
                    /*if(iLine == 15721 && name.equals("smartfox.log.2019-08-06-22")){
                        System.out.println();
                    }*/
                    if(temp.contains("Exception") && !excluded(temp) && !exceptionFind){
                        exceptionFind = true;
                        firstLine = iLine -2>=0?iLine-2:iLine;
                        System.out.println(name+","+iLine+","+firstLine+","+endLine);
                        exceptionStr.add(temp.substring(temp.indexOf(JAVA_LANG)));
                    }
                    if(!zoneFind && exceptionFind && temp.contains(ZONE_INDEX) && !temp.contains(DISCON)){
                        if(temp.contains("38")){
                            System.out.println();
                        }
                        zoneFind = true;
                        int index = temp.indexOf(ZONE_INDEX);
                        zoneStr.add(temp.substring(index,index+16));                            
                    }
                    if(iLine - firstLine >= 10 && !zoneFind && exceptionFind){
                        zoneFind = true;
                        zoneStr.add(n + " not zone");
                        n++;
                    }
                    if(!separateFind && separate.equals(temp) && zoneFind && iLine - firstLine<30){
                        separateFind = true;
                        reRead = true;
                        endLine = iLine;
                    }
                    if(iLine - firstLine>=20 && !separateFind && zoneFind){
                        separateFind = true;
                        reRead = true;
                        endLine = iLine;
                    }
                    if(reRead){
                        reRead(name,itemStr, firstLine, endLine);
                        //produce and conusme
                        String key = exceptionStr.remove(0)+separats+zoneStr.remove(0);
                        String value = itemStr.remove(0);
                        map.put(key,value);
                        mapCount.put(key, mapCount.getOrDefault(key,0)+1);
                        //reset
                        reRead = false;
                        exceptionFind = false;
                        firstLine = 0;
                        endLine = 0;
                        zoneFind = false;
                        separateFind = false;
                    }
                }
            }else{
                System.out.println("file not exist");
            }
        }
        for (Map.Entry<String, String> m : map.entrySet()) {
            String[] excAndZone = m.getKey().split(separats);
            String exc = excAndZone[0];
            String zone = excAndZone[1];
            String item = m.getValue();
            createCell(mapCount.get(m.getKey()),exc, zone, item, msheet);
        }
        HSSFRow headrow = msheet.createRow(0);
        headrow.createCell(0).setCellValue("count");
        headrow.createCell(1).setCellValue("Exception");
        headrow.createCell(2).setCellValue("zone");
        headrow.createCell(3).setCellValue("item");
        
        SimpleDateFormat smp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        String time = smp.format(new Date());
        File writeFile=new File(FILE_DIR+name.substring(13, 23)+TARGET_FILE.replace("_out", "_out_"+time));  
        OutputStream out=new FileOutputStream(writeFile);
        mwokrbook.write(out);
        out.close();
    }
    public static void reRead(String name, List<String> itemStr, int firstLine, int endLine)
            throws UnsupportedEncodingException, FileNotFoundException, IOException
    {
        boolean flag = false;
        for (File f : files) {
            if(f.getName().equals(name)){
                BufferedReader reReader = new BufferedReader(new InputStreamReader(new FileInputStream(f),"utf-8"));
                int j = 0;
                StringBuffer sb = new StringBuffer();
                String reTemp = null;
                while((reTemp=reReader.readLine())!=null){
                    j++;
                    if(j>=firstLine && j<=endLine){
                        sb.append(reTemp+SEPARATOR);
                        if(j>endLine){
                            flag = true;
                            break;
                        }
                    }
                }
                itemStr.add(sb.toString());
            }
            if(flag){
                break;
            }
        }
    }
    
    private static boolean excluded(String temp)
    {
        for (String str : excluded)
        {
            if(temp.contains(str)){
                return true;
            }
        }
        return false;
    }

    private static void createCell(int count, String exc,
            String zone, String item, HSSFSheet sheet) {
        HSSFRow dataRow = sheet.createRow(sheet.getLastRowNum()+1);
        dataRow.createCell(0).setCellValue(count);
        dataRow.createCell(1).setCellValue(exc);
        dataRow.createCell(2).setCellValue(zone);
        dataRow.createCell(3).setCellValue(item);
    }
    
    public static void main(String[] args) throws IOException {
        init();
        analysisLog();
    }
    
    private static void init()
    {
        Properties pro = new Properties();
        try
        {
            pro.load(new FileInputStream(new File(CONFIG_DIR)));
            LOG_DIR = pro.getProperty("log_dir");
            FILE_DIR = pro.getProperty("log_out");
            files = getFiles(LOG_DIR);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}