package be.ringenbeheer

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.*
import android.widget.*
import android.text.InputType
import org.json.*
import java.io.*
import java.text.NumberFormat
import java.util.*
import android.graphics.Color

data class Ring(val id: Long, val collectionId: Long, val name: String, val weight: Double, val purchase: Double,
                val purchaseDate: String, val sale: Double?, val saleDate: String, val material: String,
                val notes: String, val photo: String)
data class Collection(val id: Long, val name: String)

class Database(ctx: Context): SQLiteOpenHelper(ctx,"ringen.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE collections (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE rings (id INTEGER PRIMARY KEY AUTOINCREMENT, collection_id INTEGER NOT NULL,
            name TEXT NOT NULL, weight REAL NOT NULL, purchase REAL NOT NULL, purchase_date TEXT NOT NULL,
            sale REAL, sale_date TEXT NOT NULL DEFAULT '', material TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '', photo TEXT NOT NULL DEFAULT '')""")
        db.execSQL("INSERT INTO collections(name) VALUES ('Algemeen')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion:Int,newVersion:Int) {}
    fun collections():List<Collection> {
        val out= mutableListOf<Collection>()
        readableDatabase.rawQuery("SELECT id,name FROM collections ORDER BY name",null).use { c ->
            while(c.moveToNext()) out.add(Collection(c.getLong(0),c.getString(1)))
        }
        return out
    }
    fun rings():List<Ring> {
        val out=mutableListOf<Ring>()
        readableDatabase.rawQuery("SELECT id,collection_id,name,weight,purchase,purchase_date,sale,sale_date,material,notes,photo FROM rings ORDER BY id DESC",null).use { c ->
            while(c.moveToNext()) out.add(Ring(c.getLong(0),c.getLong(1),c.getString(2),c.getDouble(3),c.getDouble(4),
                c.getString(5),if(c.isNull(6))null else c.getDouble(6),c.getString(7),c.getString(8),c.getString(9),c.getString(10)))
        }
        return out
    }
    fun addCollection(name:String)=writableDatabase.insertOrThrow("collections",null,ContentValues().apply {put("name",name)})
    fun renameCollection(id:Long,name:String)=writableDatabase.update("collections",ContentValues().apply{put("name",name)},"id=?",arrayOf("$id"))
    fun removeCollection(id:Long) { writableDatabase.delete("collections","id=?",arrayOf("$id")) }
    fun save(r:Ring):Long {
        val v=ContentValues().apply {
            put("collection_id",r.collectionId);put("name",r.name);put("weight",r.weight);put("purchase",r.purchase)
            put("purchase_date",r.purchaseDate);if(r.sale==null)putNull("sale") else put("sale",r.sale)
            put("sale_date",r.saleDate);put("material",r.material);put("notes",r.notes);put("photo",r.photo)
        }
        return if(r.id==0L)writableDatabase.insertOrThrow("rings",null,v)
            else {writableDatabase.update("rings",v,"id=?",arrayOf("${r.id}"));r.id}
    }
    fun removeRing(id:Long)=writableDatabase.delete("rings","id=?",arrayOf("$id"))
    fun clearAll() { writableDatabase.delete("rings",null,null);writableDatabase.delete("collections",null,null) }
}

class MainActivity:Activity() {
    private lateinit var db:Database
    private lateinit var root:LinearLayout
    private var activeCollection:Long?=null
    private var editing:Ring?=null
    private var pendingPhoto:String=""
    private var photoPreview:ImageView?=null
    private val money=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("nl-BE"))
    private val PICK_PHOTO=41;private val EXPORT=42;private val IMPORT=43
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
        db = Database(this)
        dashboard()
    } catch (e: Exception) {
        android.util.Log.e("Ringenbeheer", "Opstartfout", e)

        AlertDialog.Builder(this)
            .setTitle("Ringenbeheer - Opstartfout")
            .setMessage(
                e.javaClass.simpleName + "\n\n" +
                (e.message ?: "Onbekende fout")
            )
            .setPositiveButton("Sluiten") { _, _ -> finish() }
            .show()
    }
}
    private fun text(s:String,size:Float=16f):TextView=TextView(this).apply {text=s;textSize=size;setTextColor(Color.rgb(25,43,60));setPadding(0,8,0,8)}
    private fun button(label:String,action:()->Unit):Button=Button(this).apply {text=label;setOnClickListener{action()}}
    private fun field(hint:String,value:String="",decimal:Boolean=false):EditText=EditText(this).apply {
        this.hint=hint;setText(value);setSingleLine(true)
        if(decimal)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun setup(title:String) {
        val scroll=ScrollView(this);root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(28,25,28,30)}
        scroll.addView(root);setContentView(scroll);root.addView(text(title,25f))
    }
    private fun confirm(message:String,action:()->Unit) {
        AlertDialog.Builder(this).setMessage(message).setNegativeButton("Annuleren",null).setPositiveButton("Bevestigen"){_,_->action()}.show()
    }
    private fun alert(msg:String)=AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK",null).show()
    private fun summary(list:List<Ring>):String {
        val stock=list.filter{it.sale==null};val sold=list.filter{it.sale!=null}
        return """In voorraad: ${stock.size} · ${"%.3f".format(Locale.US,stock.sumOf{it.weight})} g
Aankoopwaarde voorraad: ${money.format(stock.sumOf{it.purchase})}
Verkocht: ${sold.size} · omzet: ${money.format(sold.sumOf{it.sale?:0.0})}
Gerealiseerde winst: ${money.format(sold.sumOf{(it.sale?:0.0)-it.purchase})}"""
    }
    private fun dashboard() {
        activeCollection=null;setup("💍 Ringenbeheer")
        root.addView(text(summary(db.rings())))
        root.addView(button("＋ Collectie toevoegen"){collectionDialog(null)})
        root.addView(button("＋ Ring toevoegen"){ringForm(null)})
        root.addView(button("Alle ringen"){showRings(null)})
        db.collections().forEach { c ->
            val subset=db.rings().filter{it.collectionId==c.id}
            root.addView(button("📁 ${c.name} (${subset.size})"){showRings(c.id)})
        }
        root.addView(text("Back-up",20f))
        root.addView(button("Exporteer volledige back-up"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"ringenbeheer-backup.json")
        },EXPORT)})
        root.addView(button("Importeer back-up"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
        },IMPORT)})
        root.addView(text("De gegevens blijven lokaal op deze telefoon. Exporteer regelmatig een back-up.",13f))
    }
    private fun collectionDialog(c:Collection?) {
        val input=field("Naam collectie",c?.name?:"")
        AlertDialog.Builder(this).setTitle(if(c==null)"Nieuwe collectie" else "Collectie wijzigen")
            .setView(input).setNegativeButton("Annuleren",null).setPositiveButton("Bewaren"){_,_->
                val name=input.text.toString().trim()
                if(name.isNotEmpty()){if(c==null)db.addCollection(name) else db.renameCollection(c.id,name);dashboard()}
            }.show()
    }
    private fun showRings(collectionId:Long?) {
        activeCollection=collectionId
        val collection=db.collections().find{it.id==collectionId}
        setup(collection?.name?:"Alle ringen")
        root.addView(button("← Collecties"){dashboard()})
        val all=db.rings().filter{collectionId==null||it.collectionId==collectionId}
        root.addView(text(summary(all)))
        root.addView(button("＋ Ring toevoegen"){ringForm(null)})
        if(collection!=null){
            root.addView(button("Collectie hernoemen"){collectionDialog(collection)})
            root.addView(button("Collectie verwijderen"){if(all.isNotEmpty())alert("Verplaats of verwijder eerst de ringen in deze collectie.")
                else confirm("Collectie '${collection.name}' verwijderen?"){db.removeCollection(collection.id);dashboard()}})
        }
        val search=field("Zoeken op naam, materiaal of referentie");root.addView(search)
        val onlyStock=CheckBox(this).apply{text="Alleen in voorraad";root.addView(this)}
        val sort=Spinner(this);val modes=listOf("Nieuwste eerst","Naam A-Z","Aankoopprijs oplopend","Aankoopprijs aflopend","Gewicht aflopend")
        sort.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,modes);root.addView(sort)
        val listing=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(listing)
        fun redraw() {
            listing.removeAllViews()
            var items=all.filter{(it.name+" "+it.material).contains(search.text.toString(),true)&&(!onlyStock.isChecked||it.sale==null)}
            items=when(sort.selectedItemPosition){1->items.sortedBy{it.name.lowercase()};2->items.sortedBy{it.purchase};3->items.sortedByDescending{it.purchase};4->items.sortedByDescending{it.weight};else->items}
            items.forEach { r ->
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,12,0,12)}
                if(r.photo.isNotBlank())try {
                    val bmp=BitmapFactory.decodeFile(r.photo)
                    if(bmp!=null)row.addView(ImageView(this).apply{setImageBitmap(bmp);scaleType=ImageView.ScaleType.CENTER_CROP},
                        LinearLayout.LayoutParams(120,120))
                }catch(_:Exception){}
                val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,0,0,0)}
                info.addView(text("${r.name} · ${"%.3f".format(Locale.US,r.weight)} g"))
                info.addView(text("Aankoop ${money.format(r.purchase)} · ${if(r.sale==null)"In voorraad" else "Verkocht ${money.format(r.sale)}"}",13f))
                info.addView(button("Openen"){ringForm(r)})
                row.addView(info);listing.addView(row)
            }
        }
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=redraw()
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        onlyStock.setOnCheckedChangeListener{_,_->redraw()}
        sort.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long)=redraw()
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
        }
        redraw()
    }
    private fun ringForm(r:Ring?) {
        editing=r;pendingPhoto=r?.photo?:"";setup(if(r==null)"Ring toevoegen" else "Ring wijzigen")
        root.addView(button("← Terug"){showRings(activeCollection)})
        val collections=db.collections()
        val spinner=Spinner(this);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,collections.map{it.name})
        val chosen=collections.indexOfFirst{it.id==(r?.collectionId?:activeCollection)}
        spinner.setSelection(if(chosen<0)0 else chosen);root.addView(text("Collectie"));root.addView(spinner)
        val name=field("Naam / referentie",r?.name?:"");root.addView(name)
        val weight=field("Gewicht in gram",r?.weight?.toString()?:"",true);root.addView(weight)
        val purchase=field("Aankoopprijs (€)",r?.purchase?.toString()?:"",true);root.addView(purchase)
        val purchaseDate=field("Aankoopdatum (JJJJ-MM-DD)",r?.purchaseDate?:today());root.addView(purchaseDate)
        val material=field("Materiaal (optioneel)",r?.material?:"");root.addView(material)
        val notes=field("Opmerkingen",r?.notes?:"");root.addView(notes)
        val sold=CheckBox(this).apply{text="Verkocht";isChecked=r?.sale!=null};root.addView(sold)
        val sale=field("Verkoopprijs (€)",r?.sale?.toString()?:"",true);root.addView(sale)
        val saleDate=field("Verkoopdatum (JJJJ-MM-DD)",r?.saleDate?.takeIf{it.isNotBlank()}?:today());root.addView(saleDate)
        fun toggle(){sale.visibility=if(sold.isChecked)View.VISIBLE else View.GONE;saleDate.visibility=sale.visibility}
        sold.setOnCheckedChangeListener{_,_->toggle()};toggle()
        photoPreview=ImageView(this).apply{adjustViewBounds=true;maxHeight=420};root.addView(photoPreview)
        refreshPreview()
        root.addView(button("📷 Foto kiezen / camera"){startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*"},PICK_PHOTO)})
        root.addView(button("Bewaren"){
            val w=weight.text.toString().replace(',','.').toDoubleOrNull()
            val p=purchase.text.toString().replace(',','.').toDoubleOrNull()
            val s=if(sold.isChecked)sale.text.toString().replace(',','.').toDoubleOrNull() else null
            val pd=purchaseDate.text.toString().trim();val sd=if(sold.isChecked)saleDate.text.toString().trim() else ""
            if(name.text.isBlank()||w==null||w<0||p==null||p<0||(sold.isChecked&&(s==null||s<0))||
                !validDate(pd)||(sold.isChecked&&!validDate(sd))) {alert("Vul alle verplichte velden correct in. Datumformaat: JJJJ-MM-DD.");return@button}
            db.save(Ring(r?.id?:0,collections[spinner.selectedItemPosition].id,name.text.toString().trim(),w,p,pd,s,sd,
                material.text.toString(),notes.text.toString(),pendingPhoto))
            editing=null;showRings(activeCollection)
        })
        if(r!=null)root.addView(button("Ring verwijderen"){confirm("Ring '${r.name}' verwijderen?"){
            db.removeRing(r.id);if(r.photo.isNotBlank())File(r.photo).delete();showRings(activeCollection)
        }})
    }
    private fun today()=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private fun validDate(s:String):Boolean=try {
        val f=java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}
        s.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&f.parse(s)!=null
    }catch(_:Exception){false}
    private fun refreshPreview(){photoPreview?.setImageDrawable(null);if(pendingPhoto.isNotBlank()){
        val bmp=BitmapFactory.decodeFile(pendingPhoto);photoPreview?.setImageBitmap(bmp)
    }}
    @Deprecated("Legacy result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK||data?.data==null)return
        val uri=data.data!!
        try {when(requestCode) {
            PICK_PHOTO->{
                val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,opts)}
                val sample=(maxOf(opts.outWidth,opts.outHeight)/1200).coerceAtLeast(1)
                val decode=BitmapFactory.Options().apply{inSampleSize=sample}
                val bmp=contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,decode)}
                    ?:throw IOException("Foto niet leesbaar")
                val file=File(filesDir,"ring_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use{bmp.compress(Bitmap.CompressFormat.JPEG,80,it)}
                bmp.recycle();pendingPhoto=file.absolutePath;refreshPreview()
            }
            EXPORT->contentResolver.openOutputStream(uri)?.use{it.write(exportJson().toString().toByteArray(Charsets.UTF_8))}
            IMPORT->{
                val raw=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:throw IOException("Leeg bestand")
                val obj=JSONObject(raw);if(obj.optInt("version")!=1)throw IOException("Onbekende back-upversie")
                val collections=obj.getJSONArray("collections");val rings=obj.getJSONArray("rings")
                confirm("Back-up herstellen? Dit vervangt ALLE bestaande collecties en ringen."){try{importJson(collections,rings);dashboard()}catch(e:Exception){alert("Import mislukt: ${e.message}")}}
            }
        }}catch(e:Exception){alert("Bewerking mislukt: ${e.message}")}
    }
    private fun exportJson():JSONObject {
        val obj=JSONObject().put("version",1)
        val cs=JSONArray();db.collections().forEach{cs.put(JSONObject().put("id",it.id).put("name",it.name))}
        val rs=JSONArray();db.rings().forEach {r->
            val j=JSONObject().put("id",r.id).put("collectionId",r.collectionId).put("name",r.name)
                .put("weight",r.weight).put("purchase",r.purchase).put("purchaseDate",r.purchaseDate)
                .put("sale",r.sale?:JSONObject.NULL).put("saleDate",r.saleDate).put("material",r.material).put("notes",r.notes)
            if(r.photo.isNotBlank()&&File(r.photo).exists())
                j.put("photoBase64",android.util.Base64.encodeToString(File(r.photo).readBytes(),android.util.Base64.NO_WRAP))
            rs.put(j)
        }
        return obj.put("collections",cs).put("rings",rs)
    }
    private fun importJson(cs:JSONArray,rs:JSONArray) {
        // Validate before modifying the database.
        val parsedCollections=(0 until cs.length()).map {cs.getJSONObject(it)}
        val parsedRings=(0 until rs.length()).map {rs.getJSONObject(it)}
        parsedCollections.forEach{require(it.getString("name").isNotBlank())}
        parsedRings.forEach{require(it.getString("name").isNotBlank()&&it.getDouble("weight")>=0&&it.getDouble("purchase")>=0)}
        val oldPhotos=db.rings().map{it.photo}
        val sql=db.writableDatabase;sql.beginTransaction()
        val newFiles=mutableListOf<File>()
        try {
            db.clearAll();val idMap=mutableMapOf<Long,Long>()
            parsedCollections.forEach{c->idMap[c.getLong("id")]=db.addCollection(c.getString("name"))}
            parsedRings.forEach{j->
                var path=""
                val b64=j.optString("photoBase64","")
                if(b64.isNotBlank()){
                    val bytes=android.util.Base64.decode(b64,android.util.Base64.DEFAULT)
                    require(bytes.size<=15_000_000){"Foto te groot"}
                    val f=File(filesDir,"ring_${UUID.randomUUID()}.jpg");f.writeBytes(bytes);newFiles.add(f);path=f.absolutePath
                }
                db.save(Ring(0,idMap[j.getLong("collectionId")]?:throw IOException("Collectie ontbreekt"),
                    j.getString("name"),j.getDouble("weight"),j.getDouble("purchase"),j.getString("purchaseDate"),
                    if(j.isNull("sale"))null else j.getDouble("sale"),j.optString("saleDate"),j.optString("material"),
                    j.optString("notes"),path))
            }
            sql.setTransactionSuccessful()
        } catch(e:Exception){newFiles.forEach{it.delete()};throw e}
        finally{sql.endTransaction()}
        oldPhotos.filter{it.isNotBlank()}.forEach{File(it).delete()}
    }
}
