package tw.cmlove.taxiexam;
import java.util.*;
/** Generated from published Site v71; run tools/export-web-data.mjs to refresh. */
public final class ExamProfiles {
 public static final String[] REGIONS={"基隆市","宜蘭縣","新北市","桃園市","新竹市","新竹縣","苗栗縣","臺北市","臺中市","南投縣","彰化縣","雲林縣","嘉義縣","嘉義市","臺南市","高雄市","屏東縣","花蓮縣","臺東縣","澎湖縣","金門縣","連江縣"};
 public static final String[] REGISTRATION_REGIONS={"臺北市","新北市","基隆市","桃園市","宜蘭縣","新竹市","新竹縣","苗栗縣","臺中市","彰化縣","南投縣","雲林縣","嘉義市","嘉義縣","臺南市","高雄市","屏東縣","花蓮縣","臺東縣","澎湖縣","金門縣","連江縣"};
 public static Map<String,Integer> ratios(String city){Map<String,Integer> r=new LinkedHashMap<>();switch(city){
case "基隆市": r.put("基隆市",60);r.put("臺北市",10);r.put("新北市",10);r.put("桃園市",10);r.put("宜蘭縣",10); break;
case "臺北市": r.put("臺北市",30);r.put("新北市",30);r.put("基隆市",20);r.put("桃園市",10);r.put("宜蘭縣",10); break;
case "新北市": r.put("新北市",30);r.put("臺北市",30);r.put("基隆市",20);r.put("桃園市",10);r.put("宜蘭縣",10); break;
case "桃園市": r.put("桃園市",50);r.put("基隆市",10);r.put("臺北市",10);r.put("新北市",10);r.put("新竹市",10);r.put("新竹縣",10); break;
case "宜蘭縣": r.put("宜蘭縣",40);r.put("基隆市",20);r.put("臺北市",15);r.put("新北市",15);r.put("花蓮縣",10); break;
case "新竹市": r.put("新竹市",40);r.put("桃園市",20);r.put("新竹縣",20);r.put("苗栗縣",20); break;
case "新竹縣": r.put("新竹縣",40);r.put("桃園市",20);r.put("新竹市",20);r.put("苗栗縣",20); break;
case "苗栗縣": r.put("苗栗縣",40);r.put("新竹市",20);r.put("新竹縣",20);r.put("臺中市",20); break;
case "臺中市": r.put("臺中市",40);r.put("苗栗縣",20);r.put("彰化縣",20);r.put("南投縣",20); break;
case "彰化縣": r.put("彰化縣",40);r.put("臺中市",20);r.put("南投縣",20);r.put("雲林縣",20); break;
case "南投縣": r.put("南投縣",40);r.put("臺中市",30);r.put("彰化縣",30); break;
case "雲林縣": r.put("雲林縣",40);r.put("彰化縣",20);r.put("嘉義市",20);r.put("嘉義縣",20); break;
case "嘉義市": r.put("嘉義市",40);r.put("雲林縣",20);r.put("嘉義縣",20);r.put("臺南市",20); break;
case "嘉義縣": r.put("嘉義縣",40);r.put("雲林縣",20);r.put("嘉義市",20);r.put("臺南市",20); break;
case "臺南市": r.put("臺南市",40);r.put("嘉義市",20);r.put("嘉義縣",20);r.put("高雄市",20); break;
case "高雄市": r.put("高雄市",40);r.put("臺南市",30);r.put("屏東縣",30); break;
case "屏東縣": r.put("屏東縣",40);r.put("高雄市",30);r.put("臺東縣",30); break;
case "花蓮縣": r.put("花蓮縣",40);r.put("宜蘭縣",30);r.put("臺東縣",30); break;
case "臺東縣": r.put("臺東縣",40);r.put("花蓮縣",30);r.put("屏東縣",30); break;
case "澎湖縣": r.put("澎湖縣",100); break;
case "金門縣": r.put("金門縣",100); break;
case "連江縣": r.put("連江縣",100); break;
 default:throw new IllegalArgumentException("請選擇報考縣市");}return r;}
}
