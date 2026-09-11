package tw.cmlove.taxiexam;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only downloads question data from the existing public question-bank endpoint. */
public final class BankStore {
    public static final String BASE="https://cmlovehtc.github.io/taxi/";
    public static final class Bank {
        public final List<QuizCore.Question> questions;
        public final Map<String,QuizCore.Question> byId;
        public final JSONObject snapshot;
        public final String date;
        Bank(List<QuizCore.Question> q,JSONObject data,String date) { this.questions=Collections.unmodifiableList(q);snapshot=data;this.date=date;Map<String,QuizCore.Question> map=new HashMap<>();for(QuizCore.Question v:q)map.put(v.id,v);byId=Collections.unmodifiableMap(map); }
    }
    private final Context context;
    public BankStore(Context context) { this.context=context; }
    public Bank load() throws Exception {
        AtomicFile cache=new AtomicFile(new File(context.getFilesDir(),"question-bank.json"));
        try { return validate(new JSONObject(read(cache.openRead(),15*1024*1024))); } catch(Exception ignored) { }
        JSONObject manifest=new JSONObject(read(context.getAssets().open("question-bank/question-bank-manifest.json"),1024*1024));
        JSONObject files=new JSONObject(); Iterator<String> it=manifest.getJSONObject("sha256").keys();
        while(it.hasNext()) { String name=it.next();validateName(name); files.put(name,read(context.getAssets().open("question-bank/"+name),2*1024*1024)); }
        return validate(new JSONObject().put("manifest",manifest).put("files",files));
    }
    public Bank update(Bank current) throws Exception {
        JSONObject manifest=new JSONObject(fetch("question-bank-manifest.json"));
        JSONObject hashes=manifest.getJSONObject("sha256"); JSONObject files=new JSONObject();
        JSONObject existing=current.snapshot.getJSONObject("files");
        Iterator<String> it=hashes.keys(); while(it.hasNext()) {
            String name=it.next();validateName(name);String old=existing.optString(name,"");
            String data=QuizCore.sha256(old.getBytes(StandardCharsets.UTF_8)).equalsIgnoreCase(hashes.getString(name)) ? old : fetch(name);
            if(!QuizCore.sha256(data.getBytes(StandardCharsets.UTF_8)).equalsIgnoreCase(hashes.getString(name))) throw new IOException("題庫正在更新，請稍後重試；手機內題庫仍可使用。");
            files.put(name,data);
        }
        JSONObject snapshot=new JSONObject().put("manifest",manifest).put("files",files);
        Bank next=validate(snapshot);writeAtomic(new File(context.getFilesDir(),"question-bank.json"),snapshot.toString());return next;
    }
    private static Bank validate(JSONObject data) throws Exception {
        JSONObject manifest=data.getJSONObject("manifest"), hashes=manifest.getJSONObject("sha256"),files=data.getJSONObject("files");
        if(manifest.getInt("schema_version")!=1 || hashes.length()<12 || hashes.length()>100 || manifest.getInt("files")!=hashes.length())throw new IOException("不支援的題庫版本");
        TreeSet<String> names=new TreeSet<>();Iterator<String> iterator=hashes.keys();while(iterator.hasNext())names.add(iterator.next());
        List<QuizCore.Question> questions=new ArrayList<>();
        for(String name:names) {validateName(name);String raw=files.getString(name);if(!QuizCore.sha256(raw.getBytes(StandardCharsets.UTF_8)).equalsIgnoreCase(hashes.getString(name)))throw new IOException("題庫校驗失敗："+name);questions.addAll(QuizCore.parse(name,raw));}
        if(questions.size()!=manifest.getInt("questions"))throw new IOException("題目數量不符，保留原題庫");
        for(String city:QuizCore.CITIES) for(boolean tf:new boolean[]{true,false}) {boolean found=false;for(QuizCore.Question q:questions)if(q.city.equals(city)&&q.tf==tf){found=true;break;}if(!found)throw new IOException("缺少 "+city+" 題庫");}
        return new Bank(questions,data,manifest.getString("updated_at"));
    }
    private static void validateName(String name) throws IOException {
        if(!name.matches("(?:交通法令|[\\u4e00-\\u9fff]+_地理環境)_(?:是非題|選擇題)\\.txt"))throw new IOException("題庫檔名不符");
    }
    private static String fetch(String name) throws Exception {
        URL url=new URL(BASE+URLEncoder.encode(name,"UTF-8").replace("+","%20"));
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(12000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Cache-Control","no-cache");connection.setRequestProperty("User-Agent","TaxiExamAndroid/0.1");
        try {if(connection.getResponseCode()!=200)throw new IOException("暫時無法取得題庫（"+connection.getResponseCode()+"）");return read(connection.getInputStream(),2*1024*1024);}finally{connection.disconnect();}
    }
    public static String read(InputStream input,int max) throws IOException {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("檔案超過大小限制");out.write(buffer,0,n);}return out.toString("UTF-8");}
    }
    public static void writeAtomic(File target,String text) throws IOException {
        AtomicFile file=new AtomicFile(target);FileOutputStream out=null;
        try {out=file.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
}
