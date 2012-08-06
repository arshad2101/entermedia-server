package org.openedit.entermedia.modules;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Attribute;
import org.entermedia.attachments.AttachmentManager;
import org.entermedia.upload.FileUpload;
import org.entermedia.upload.FileUploadItem;
import org.entermedia.upload.UploadRequest;
import org.openedit.Data;
import org.openedit.data.BaseData;
import org.openedit.data.FilteredTracker;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
import org.openedit.data.Searcher;
import org.openedit.data.lucene.DocumentData;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.Category;
import org.openedit.entermedia.CompositeAsset;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.creator.ConvertInstructions;
import org.openedit.entermedia.creator.MediaCreator;
import org.openedit.entermedia.edit.AssetEditor;
import org.openedit.entermedia.scanner.AssetImporter;
import org.openedit.entermedia.xmp.MetadataWriter;
import org.openedit.entermedia.xmp.XmpWriter;
import org.openedit.event.WebEventListener;
import org.openedit.events.PathEventManager;
import org.openedit.repository.Repository;
import org.openedit.repository.RepositoryException;
import org.openedit.repository.filesystem.FileRepository;
import org.openedit.util.DateStorageUtil;
import org.openedit.xml.ElementData;

import com.openedit.OpenEditException;
import com.openedit.WebPageRequest;
import com.openedit.WebServer;
import com.openedit.hittracker.HitTracker;
import com.openedit.hittracker.ListHitTracker;
import com.openedit.hittracker.SearchQuery;
import com.openedit.page.Page;
import com.openedit.users.User;
import com.openedit.util.PathUtilities;

public class AssetEditModule extends BaseMediaModule
{
	protected WebServer fieldWebServer;
	protected WebEventListener fieldWebEventListener;
	protected static final String CATEGORYID = "categoryid";
	protected FileUpload fieldFileUpload;
	public AttachmentManager getAttachmentManager() {
		return fieldAttachmentManager;
	}
	public void setAttachmentManager(AttachmentManager fieldAttachmentManager) {
		this.fieldAttachmentManager = fieldAttachmentManager;
	}

	protected AssetImporter fieldAssetAssetImporter;
	protected AttachmentManager fieldAttachmentManager;
	
	private static final Log log = LogFactory.getLog(AssetEditModule.class);
	
	public List getUploadedPages(WebPageRequest inReq)
	{
		List pages = new ArrayList();

		List unzipped = (List) inReq.getPageValue("unzippedfiles");
		if(unzipped != null && unzipped.size() > 0)
		{
			pages.addAll(unzipped);
		}
		else
		{
			UploadRequest map = (UploadRequest) inReq.getPageValue("uploadrequest");
			if (map != null)
			{
				List uploadItems = map.getUploadItems();
				if (uploadItems != null)
				{
					for (Iterator iterator = uploadItems.iterator(); iterator.hasNext();)
					{
						FileUploadItem uploadItem = (FileUploadItem) iterator.next();
						Page uploaded = uploadItem.getSavedPage();
						pages.add(uploadItem.getSavedPage());
					}
				}
			}		
		}
		
		return pages;
	}
	public boolean makeFolderAsset(WebPageRequest inReq) throws Exception
	{
		Asset asset = getAsset(inReq);
		if(asset == null)
		{
			return false;
		}
		if(asset.isFolder())
		{
			return true;
		}
		else
		{
			return getAssetEditor(inReq).makeFolderAsset(asset, inReq.getUser());
		}
	}

	public void writeXmpData(WebPageRequest inReq) throws Exception
	{
		XmpWriter writer = (XmpWriter) getBeanFactory().getBean("xmpWriter");
		String assetid = inReq.getRequestParameter("assetid");
		MediaArchive mediaArchive = getMediaArchive(inReq);
		Asset asset = mediaArchive.getAsset(assetid);
		
		boolean didSave = false;
		didSave = writer.saveMetadata(mediaArchive, asset);
		inReq.putPageValue("didSave", new Boolean(didSave));
	}
	
	public AssetEditor getAssetEditor(WebPageRequest inContext) throws OpenEditException 
	{
		MediaArchive mediaarchive = getMediaArchive(inContext);
		AssetEditor editor = (AssetEditor) inContext
				.getSessionValue("AssetEditor" + mediaarchive.getCatalogId());
		if (editor == null)
		{
			editor = (AssetEditor)getModuleManager().getBean("assetEditor");
			editor.setMediaArchive(mediaarchive);
			
			inContext.putSessionValue("AssetEditor" + mediaarchive.getCatalogId(),
					editor);
		}
		inContext.putPageValue("AssetEditor", editor);
		
		return editor;
	}

	public void copyAsset(WebPageRequest inContext) throws OpenEditException 
	{
		Asset asset = getAsset(inContext);

		String originalsourcepath = asset.getSourcePath();
		AssetEditor editor = getAssetEditor(inContext);

		String targetName = inContext.getRequestParameter("name");
		String newSourcePath;
		String sourceDirectory = inContext.findValue("defaultsourcedirectory");
		if(sourceDirectory == null)
		{
			sourceDirectory = PathUtilities
				.extractDirectoryPath(originalsourcepath);
			if (originalsourcepath.endsWith("/"))
			{
				sourceDirectory = PathUtilities
					.extractDirectoryPath(sourceDirectory);
			}
		}
		if (sourceDirectory.endsWith("/"))
		{
			sourceDirectory = sourceDirectory.substring(0, sourceDirectory.length() - 2);
		}
		String newId = editor.getMediaArchive().getAssetSearcher()
		.nextAssetNumber();
		
		boolean createAsFolder = Boolean.parseBoolean(inContext.findValue("createasfolder"));

		if(targetName != null) // Is this really used? Seems wrong somehow...
		{
			 newSourcePath = sourceDirectory + targetName + "/";
		}
		else
		{
			if (createAsFolder)
			{
				newSourcePath = sourceDirectory + "/" + newId;
			}
			else
			{
				newSourcePath = sourceDirectory + "/" +newId + ".data";
			}
		}
		if(newSourcePath.startsWith("/"))
		{
			newSourcePath = newSourcePath.substring(1);
		}
		
		if (newSourcePath.equals(originalsourcepath))
		{
			return; //can't copy to itself
		}
		
		Asset newasset = editor.copyAsset(asset, newId, newSourcePath);
		
		newasset.setFolder(createAsFolder);
				
		//Copy any images or folders using OE File Manager
		String newpath = "/WEB-INF/data/" + editor.getMediaArchive().getCatalogId() + "/originals/" + newSourcePath + "/";
		String oldpath = "/WEB-INF/data/" + editor.getMediaArchive().getCatalogId() + "/originals/" + originalsourcepath + "/";
		
		Page newpage = getPageManager().getPage(newpath);
		Page oldpage = getPageManager().getPage(oldpath);

		//Check for flag indicating that the image should not be copied
		boolean copyimage = Boolean.parseBoolean(inContext.findValue("copyimage"));
		if( !copyimage )
		{
			//Remove the image reference from the xconf
			newasset.removeProperties(new String[] {
					"originalfile", "primaryfile",
					"fileformat", "imagefile"});
			//create a blank directory 
			getPageManager().putPage(newpage);
		}
		else
		{
			//copy the original assets directory (including the image)
			getPageManager().copyPage(oldpage, newpage);
		}
	
		newasset.setName(targetName);
	
		Collection categories = asset.getCategories();
		for (Iterator iter = categories.iterator(); iter.hasNext();) {
			Category element = (Category) iter.next();
			newasset.addCategory(element);
		}

		Page oldPage = getPageManager().getPage(
				editor.getMediaArchive().getCatalogHome() + "/assets/"
						+ asset.getSourcePath() + ".html");
		if (oldPage.exists()) {
			Page newPage = getPageManager().getPage(
					editor.getMediaArchive().getCatalogHome() + "/assets/"
							+ newasset.getSourcePath() + ".html");
			try {
				getPageManager().copyPage(oldPage, newPage);
			} catch (RepositoryException re) {
				throw new OpenEditException(re);
			}
		}
		
		// Remove the PDF text
		newasset.removeProperty("fulltext");
		editor.setCurrentAsset(newasset);
		editor.getMediaArchive().saveAsset(newasset, inContext.getUser());
		inContext.setRequestParameter("assetid", newasset.getId());
		if( inContext.getRequestParameters("field") != null)
		{
			saveAssetProperties(inContext);
		}
		inContext.setRequestParameter("assetid", asset.getId());
		inContext.setRequestParameter("targetsourcepath", newasset.getSourcePath());
		inContext.setRequestParameter("newassetid", newasset.getId());
		inContext.putPageValue("target", newasset);

		copyJoinData(asset, newasset);
	}

	
	protected void copyJoinData(Asset source, Asset target)
	{
		PropertyDetails properties = getMediaArchive(source.getCatalogId()).getAssetPropertyDetails();
		List lists = properties.getDetailsByProperty("type", "textjoin");
		lists.addAll(properties.getDetailsByProperty("type", "datejoin"));
		HashSet processed = new HashSet();
		for (Iterator iterator = lists.iterator(); iterator.hasNext();)
		{
			PropertyDetail detail = (PropertyDetail) iterator.next();
			String detailid = detail.getId();
			if (detailid.indexOf(".") > 0)
			{
				detailid = detailid.split("\\.")[0];
			}
			if (processed.contains(detailid))
			{
				continue;
			}
			else
			{
				processed.add(detailid);
			}
			
			FilteredTracker tracker = new FilteredTracker();
			tracker.setSearcher(getSearcherManager().getSearcher(detail.getCatalogId(), detailid));
			tracker.filter("assetid", source.getId());
			HitTracker hits = tracker.filtered();
			
			Searcher targetSearcher = getSearcherManager().getSearcher(target.getCatalogId(), detailid);
			if (targetSearcher != null && hits != null && hits.size() > 0)
			{
				List data = new ArrayList();
				for (Iterator iterator2 = hits.iterator(); iterator2.hasNext();)
				{
					Data sourcedata = (Data)iterator2.next();
					ElementData item = null;
					if(sourcedata instanceof DocumentData){
						item = (ElementData) targetSearcher.searchById(sourcedata.getId());
					} else{
						item = (ElementData) sourcedata;
					}
					if(item == null){
						continue;
					}
					
					Data newItem = targetSearcher.createNewData();
					newItem.setSourcePath(target.getSourcePath());
					for (Iterator iterator3 = item.getElement().attributes().iterator(); iterator3.hasNext();)
					{
						Attribute property = (Attribute) iterator3.next();
						if (property.getName().equals("assetid"))
						{
							newItem.setProperty("assetid", target.getId());
						}
						else if (!property.getName().equals("id"))
						{
							newItem.setProperty(property.getName(), property.getValue());
						}
						
					}
					
					data.add(newItem);
				}
				targetSearcher.saveAllData(data, null);
			}
		}
	}	
	
	public void saveAssetResultsEdits(WebPageRequest inRequest) throws OpenEditException 
	{
		MediaArchive store = getMediaArchive(inRequest);
		String[] fields = inRequest.getRequestParameters("editfield");
		if (fields != null) {
			for (int i = 0; i < fields.length; i++) {
				String key = fields[i];
				String assetid = key.substring(0, key.indexOf("."));
				String fieldid = key.substring(key.indexOf(".") + 1);
				String value = inRequest.getRequestParameter(key);
				Asset asset = store.getAsset(assetid);
				if (asset == null) {
					throw new OpenEditException("Asset is not found " + key);
				}
				String oldvalue = asset.getProperty(key);
				asset.setProperty(fieldid, value);
				// null check
				if (value != null && !value.equals(oldvalue)) {
					store.getAssetSearcher().saveData(asset,
							inRequest.getUser());
				} else if (oldvalue != null && !oldvalue.equals(value)) {
					store.getAssetSearcher().saveData(asset,
							inRequest.getUser());
				}
			}
		}
	}

	/**
	 * Removes generated images (medium, thumbs, etc) for a asset.
	 * 
	 * @param inRequest
	 *            The web request. Needs a <code>assetid</code> or
	 *            <code>sourcePath</code> request parameter.
	 */
	public void removeAssetImages(WebPageRequest inRequest)
	{
		Asset asset = getAsset(inRequest);
		MediaArchive archive = getMediaArchive(inRequest);
		
		archive.removeGeneratedImages(asset);
	}
	public Data createMultiEditData(WebPageRequest inReq) throws Exception
	{
		String hitsname = inReq.getRequestParameter("multihitsname");//expects session id
		if( hitsname == null)
		{
			return null;
		}
		MediaArchive store = getMediaArchive(inReq);
		HitTracker hits = (HitTracker) inReq.getSessionValue(hitsname);
		if( hits == null)
		{
			log.error("Could not find " + hitsname);
			return null;
		}
		CompositeAsset composite = new CompositeAsset();
		for (Iterator iterator = hits.iterator(); iterator.hasNext();)
		{
			Object target = (Object) iterator.next();
			Asset p = null;
			if( target instanceof Asset)
			{
				p = (Asset)target;
			}
			else
			{
				String id = hits.getValue(target, "id");
				p = store.getAsset(id);
			}
			if( p != null)
			{
				composite.addData(p);
			}
		}
		composite.setId("multiedit:"+hitsname);
		//set request param?
		inReq.setRequestParameter("assetid",composite.getId());
		inReq.putPageValue("data", composite);
		inReq.putPageValue("asset", composite);
		inReq.putSessionValue(composite.getId(), composite);
		
		return composite;
	}
	
	
	public void selectAsset(WebPageRequest inContext) throws OpenEditException 
	{
		AssetEditor editor = getAssetEditor(inContext);
		Object assetPageValue = inContext.getPageValue("asset");
		Asset asset = null;
		if (assetPageValue != null && assetPageValue instanceof Asset)
		//it may be a CompositeAsset, which would throw an exception
		{
			asset = (Asset) assetPageValue;
		}
		
		if (asset == null) {
			String id = inContext.getRequestParameter("assetid");
			if (id == null) {
				id = PathUtilities.extractPageName(inContext.getContentPage()
						.getPath());
			}
			if (id != null) {
				asset = editor.getAsset(id);
			}
		}
		if (asset == null) {
			return;
		}
		editor.setCurrentAsset(asset);
		inContext.putPageValue("asset", asset);
	}
	
	public void deleteAssets(WebPageRequest inContext) throws OpenEditException 
	{
		AssetEditor editor = getAssetEditor(inContext);
		String[] assetIds = inContext.getRequestParameters("assetid");
		
		Asset asset;
		
		for (int i = 0; i < assetIds.length; i++) {
			if (assetIds[i].startsWith("multiedit:"))
			{
				try
				{
					CompositeAsset assets = (CompositeAsset) inContext.getSessionValue(assetIds[i]);
					for (Iterator iterator = assets.iterator(); iterator
							.hasNext();) {
						asset = (Asset) iterator.next();
						Page original = editor.getMediaArchive().getOriginalDocument(asset);
						editor.deleteAsset(asset);
						String ok = inContext.getRequestParameter("deleteoriginal");
						if( Boolean.parseBoolean(ok))
						{
							getPageManager().removePage(original);
						}
					}
				} 
				catch (Exception e)
				{
					continue;
				}
			}
			else
			{
				asset = editor.getAsset(assetIds[i]);
				if (asset != null) 
				{
					Page original = editor.getMediaArchive().getOriginalDocument(asset);
					editor.deleteAsset(asset);
					String ok = inContext.getRequestParameter("deleteoriginal");
					if( Boolean.parseBoolean(ok))
					{
						getPageManager().removePage(original);
					}
				}
			}
		}
	}
	public void saveAsset(WebPageRequest inContext) throws OpenEditException {
		String saveAsNew = inContext.getRequestParameter("saveasnew");
		AssetEditor editor = getAssetEditor(inContext);
		Asset asset = editor.getCurrentAsset();

		String newId = inContext.getRequestParameter("newassetid");
		// was id changed?
		if (newId != null && !newId.equals(asset.getId())) {
			Asset newasset = editor.copyAsset(asset, newId);
			Collection catalogs = asset.getCategories();
			for (Iterator iter = catalogs.iterator(); iter.hasNext();) {
				Category element = (Category) iter.next();
				newasset.addCategory(element);
			}
			if (saveAsNew == null || saveAsNew.equalsIgnoreCase("false")) {
				Page oldPage = getPageManager().getPage(
						editor.getMediaArchive().getCatalogHome() + "/assets/"
								+ asset.getId() + ".html");
				if (oldPage.exists()) {
					Page newPage = getPageManager().getPage(
							editor.getMediaArchive().getCatalogHome() + "/assets/"
									+ newasset.getId() + ".html");
					try {
						getPageManager().movePage(oldPage, newPage);
					} catch (RepositoryException re) {
						throw new OpenEditException(re);
					}
				}

				editor.deleteAsset(asset); // changing asset id, and erase
												// the old id
				// editor.getMediaArchive().reindexAll();
			} else {
				Page oldPage = getPageManager().getPage(
						editor.getMediaArchive().getCatalogHome() + "/assets/"
								+ asset.getId() + ".html");
				if (oldPage.exists()) {
					Page newPage = getPageManager().getPage(
							editor.getMediaArchive().getCatalogHome() + "/assets/"
									+ newasset.getId() + ".html");
					try {
						getPageManager().copyPage(oldPage, newPage);
					} catch (RepositoryException re) {
						throw new OpenEditException(re);
					}
				}
			}
			asset = newasset;
		}

		asset.setName(inContext.getRequestParameter("name"));
		// asset.setDescription( inContext.getRequestParameter( "description"
		// ) );

		editor.getMediaArchive().saveAsset(asset, inContext.getUser());
		asset = editor.getAsset(asset.getId());
		editor.setCurrentAsset(asset);

		inContext.putPageValue("asset", asset);
		inContext.setRequestParameter("assetid", asset.getId());
	}
	
	protected MetadataWriter getKeywordWriter( WebPageRequest inRequest )
	{
		String catalogid = inRequest.findValue("catalogid");
		return (MetadataWriter)getModuleManager().getBean(catalogid, "keywordWriter");
	}
	public void addAssetValues(WebPageRequest inReq) throws OpenEditException 
	{
		Asset asset = getAsset(inReq);
		String inFieldName = inReq.getRequestParameter("fieldname");
		Collection existing = asset.getValues(inFieldName);
		String value = inReq.getRequestParameter(inFieldName + ".value");
		if( existing == null)
		{
			existing = new ArrayList();
		}
		else
		{
			existing = new ArrayList(existing);
		}
		if( !existing.contains(value))
		{
			existing.add(value);
			asset.setValues(inFieldName, existing);
			getMediaArchive(inReq).saveAsset(asset, inReq.getUser());
		}
	}	
	public void removeAssetValues(WebPageRequest inReq) throws OpenEditException 
	{
		Asset asset = getAsset(inReq);
		String inFieldName = inReq.getRequestParameter("fieldname");
		Collection existing = asset.getValues(inFieldName);
		String value = inReq.getRequestParameter(inFieldName + ".value");
		if( existing == null)
		{
			existing = new ArrayList();
		}
		else
		{
			existing = new ArrayList(existing);
		}
		existing.remove(value);
		asset.setValues(inFieldName, existing);
		getMediaArchive(inReq).saveAsset(asset, inReq.getUser());
	}
	public void addAssetKeyword(WebPageRequest inReq) throws OpenEditException 
	{
		AssetEditor editor = getAssetEditor(inReq);
		String key = inReq.getRequestParameter("keyword");
		if (key == null) {
			return;
		}
		Asset asset;
		String id = inReq.getRequestParameter("assetid");
		MetadataWriter writer = getKeywordWriter(inReq);
		
		if (id == null)
		{
			asset = editor.getCurrentAsset();
			writer.addAssetForWriting(asset);
		}
		else if (id.startsWith("multiedit:"))
		{
			CompositeAsset composite = (CompositeAsset) inReq.getSessionValue(id);
			composite.addKeyword(key);
			for(Data data: composite.getItems())
			{
				writer.addAssetForWriting((Asset) data);
				editor.getMediaArchive().saveAsset((Asset) data,inReq.getUser());

				getMediaArchive(inReq).fireMediaEvent("asset/keywordsmodified", inReq.getUser(), (Asset)data);
			}
			return;
		}
		else
		{
			asset = getMediaArchive(inReq).getAsset(id);
			writer.addAssetForWriting(asset);
		}
		asset.addKeyword(key);
		editor.getMediaArchive().saveAsset(asset,inReq.getUser());
		getMediaArchive(inReq).fireMediaEvent("asset/keywordsmodified", inReq.getUser(), asset);
	}

	public void removeAssetKeyword(WebPageRequest inReq) throws OpenEditException 
	{
		AssetEditor editor = getAssetEditor(inReq);
		String key = inReq.getRequestParameter("keyword");
		if (key == null) {
			return;
		}
		Asset asset;
		String id = inReq.getRequestParameter("assetid");
		MetadataWriter writer = getKeywordWriter(inReq);
		if (id == null)
		{
			asset = editor.getCurrentAsset();
			writer.addAssetForWriting(asset);
		}
		else if (id.startsWith("multiedit:"))
		{
			CompositeAsset composite = (CompositeAsset) inReq.getSessionValue(id);
			composite.removeKeyword(key);
			
			for(Data data: composite.getItems())
			{
				editor.getMediaArchive().saveAsset((Asset) data,inReq.getUser());
				writer.addAssetForWriting((Asset) data);
				getMediaArchive(inReq).fireMediaEvent("asset/keywordsmodified", inReq.getUser(), (Asset)data);
			}
			return;
		}
		else
		{
			asset = getMediaArchive(inReq).getAsset(id);
			writer.addAssetForWriting(asset);
		}
		asset.removeKeyword(key);
		editor.getMediaArchive().saveAsset(asset,inReq.getUser());

		getMediaArchive(inReq).fireMediaEvent("asset/keywordsmodified", inReq.getUser(), asset);
	}
	
	public void writeAssetKeywords( WebPageRequest inRequest )
	{
		getKeywordWriter(inRequest).writeAssets();
	}
	
	public void saveAssetProperties(WebPageRequest inReq) throws OpenEditException 
	{
		MediaArchive archive = getMediaArchive(inReq);
		String[] fields = inReq.getRequestParameters("field");
		if (fields == null) {
			return;
		}
		String assetid = inReq.getRequestParameter("assetid");
		//<input type="hidden" name="assetid" value="$asset.id"/>
		Asset asset = getAsset(inReq);
		archive.getAssetSearcher().saveDetails(inReq, fields, asset, assetid);
	}
	
	public void attachToAssetFromUploads(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		//String basepath  = "/WEB-INF/data" + archive.getCatalogHome() + "/temp/" + inReq.getUserName() + "/";
		Asset asset = getAsset(inReq);
		List<Page> temppages = getUploadedPages(inReq);
		
		//copy the temppages in to the originals folder, but first check if this is a folder based asset
		if(!asset.isFolder())
		{
			makeFolderAsset(inReq);
		}
		
		//move the pages
		String destination = "/WEB-INF/data" + archive.getCatalogHome() + "/originals/" + asset.getSourcePath();
		Page dest = getPageManager().getPage(destination);
		if(!dest.exists()){
			log.info("Could not attach file destination folder didn't exist: " + dest.getPath());
		}
		for (Iterator<Page> iterator = temppages.iterator(); iterator.hasNext();)
		{
			Page page = (Page) iterator.next();
			if(!page.exists()){
				log.info("Could not attach file temp file doesn't exist: " + page.getPath());
			}
			getPageManager().movePage(page, dest);
		}
		getAttachmentManager().processAttachments(archive, asset, false);//don't reprocess everything else
		
		inReq.putPageValue("asset", asset);
	}
	
	public void createAssetFromUploads(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		String basepath  = "/WEB-INF/data" + archive.getCatalogHome() + "/temp/" + inReq.getUserName() + "/";

		createAssetsFromPages(getUploadedPages(inReq),basepath,inReq);
	}

	public void createAssetsFromFile(WebPageRequest inReq)
	{
		String sourcepath = inReq.findValue("sourcepath");
		String catalogid = inReq.findValue("catalogid");
		String unzip = inReq.findValue("unzip");
		
		Data asset = getAssetImporter().createAssetFromExistingFile(getMediaArchive(catalogid), inReq.getUser(), Boolean.valueOf(unzip), sourcepath);
		if(asset == null)
		{
			return;
		}
		if( asset instanceof CompositeAsset)
		{
			asset.setId("multiedit:new");
			inReq.putSessionValue(asset.getId(), asset);
		}
		inReq.setRequestParameter("id", asset.getId());
		inReq.putPageValue("asset", asset);
	}
	public void createAssetsFromTemp(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		ListHitTracker tracker = new ListHitTracker();
		tracker.getSearchQuery().setCatalogId(archive.getCatalogId());
		
		String[] uploadprefixes = inReq.getRequestParameters("uploadprefix");

		//String basepath  = "/WEB-INF/data" + archive.getCatalogHome() + "/temp/" + inReq.getUserName() + "/";
		for (int i = 0; i < uploadprefixes.length; i++)
		{
			String path = inReq.getRequestParameter(uploadprefixes[i] + "temppath");
			path = path.replace("\r\n","");
			path = path.replace("\n", "");
			path = path.replace("\r", "");
			Page page = getPageManager().getPage(path);
			if(!page.exists()){
				log.info("Page: " + page.getPath() + " does not exist");
			}
			readMetaData(inReq, archive,uploadprefixes[i], page, tracker);
		}
		
		//set the group view permissions if something was passed in
		findUploadTeam(inReq, archive, tracker);
		//TODO: Move into the loop
		archive.saveAssets(tracker, inReq.getUser());

		
		String hitsname = inReq.findValue("hitsname");
		if (hitsname != null)
		{
			tracker.getSearchQuery().setHitsName(hitsname);
		}
		inReq.putSessionValue(tracker.getSessionId(), tracker);
		inReq.putPageValue(tracker.getHitsName(), tracker);
		
		List allids = new ArrayList();
		for (Iterator iterator = tracker.iterator(); iterator.hasNext();)
		{
			Asset asset = (Asset) iterator.next();
			allids.add(asset.getId());
			archive.fireMediaEvent("importing/assetuploaded",inReq.getUser(),asset);
		}
		Asset sample = (Asset)tracker.first();
		if( sample != null)
		{
			archive.fireMediaEvent("importing/assetsuploaded",inReq.getUser(),sample,allids);
		}
		
	}
	protected void findUploadTeam(WebPageRequest inReq, MediaArchive archive, ListHitTracker tracker)
	{
		String groupid = inReq.getRequestParameter("viewgroup");
		if(groupid != null)
		{
			for (Iterator iterator = tracker.iterator(); iterator.hasNext();)
			{
				Asset asset = (Asset) iterator.next();
				asset.setProperty("uploadteam", groupid);
				archive.getAssetSecurityArchive().grantGroupViewAccess(archive, groupid, asset);
			}
			
		}
	}
	protected void createAssetsFromPages(List<Page> inPages, String inBasepath, WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		
		ListHitTracker tracker = new ListHitTracker();
		tracker.getSearchQuery().setCatalogId(archive.getCatalogId());
//		List<String> allids = new ArrayList();
		
		for (Iterator iterator = inPages.iterator(); iterator.hasNext();)
		{
			Page page = (Page) iterator.next();
			readMetaData(inReq, archive,"", page, tracker);
		}
//		for (Iterator iterator = tracker.iterator(); iterator.hasNext();)
//		{
//			Asset asset = (Asset) iterator.next();
//			
//		}		
		//set the group view permissions if something was passed in
		findUploadTeam(inReq, archive, tracker);

		archive.saveAssets(tracker, inReq.getUser());

		String hitsname = inReq.findValue("hitsname");
		if (hitsname != null)
		{
			tracker.getSearchQuery().setHitsName(hitsname);
		}
		inReq.putSessionValue(tracker.getSessionId(), tracker);
		inReq.putPageValue(tracker.getHitsName(), tracker);
		inReq.putPageValue("uploadedassets",tracker); 

		List allids = new ArrayList();
		//Events are not dependable. We should probably just run the event directly
		for (Iterator iterator = tracker.iterator(); iterator.hasNext();)
		{
			Asset asset = (Asset) iterator.next();
			allids.add(asset.getId());
			inReq.setRequestParameter("assetid", asset.getId() );
			//archive.fireMediaEvent("importing/assetuploaded",inReq.getUser(),asset);
		}
		Asset sample = (Asset)tracker.first();
		if( sample != null)
		{
			archive.fireMediaEvent("importing/assetsuploaded",inReq.getUser(),sample,allids);
		}
	}
	protected void readMetaData(WebPageRequest inReq, MediaArchive archive, String prefix, Page inPage, ListHitTracker output)
	{
		String[] fields = inReq.getRequestParameters("field");
		Map vals = new HashMap();
		if( fields != null)
		{
			for (int i = 0; i < fields.length; i++)
			{
				String val = inReq.getRequestParameter(prefix + fields[i]+ ".value");
				if( val != null)
				{
					vals.put(fields[i],val);
				}
			}
		}
		String[] categories = inReq.getRequestParameters(prefix + "categoryid");
		List cats = new ArrayList();
		if( categories != null)
		{
			for (int i = 0; i < categories.length; i++)
			{
				Category cat = archive.getCategory(categories[i]);
				if( cat != null)
				{
					cats.add(cat);
				}
			}
		}

		//This is old dont use
		categories = inReq.getRequestParameters(prefix + "categories");
		if( categories != null)
		{
			for (int i = 0; i < categories.length; i++)
			{
				Category cat = archive.getCategory(categories[i]);
				if( cat != null)
				{
					cats.add(cat);
				}
			}
		}
		String sourcepath = inReq.findValue(prefix + "sourcepath");
		if(sourcepath == null){
			sourcepath = "";
		}
		if( sourcepath.endsWith("/"))
		{
			sourcepath = sourcepath.substring(0,sourcepath.length() - 1);
		}
		String filename = inPage.getName();
		if(filename.startsWith("tmp") && filename.indexOf('_') > -1)
		{
			filename = filename.substring(filename.indexOf('_') + 1);
		}
		String assetsourcepath = sourcepath + "/" + filename; //TODO: Should we save like /a/allstuff.jpg
		//getPageManager().clearCache(inPage);
		Asset existing = archive.getAssetBySourcePath(assetsourcepath);
		Asset asset = new Asset();
		
		if (existing != null) 
		{
			asset.setId(archive.getAssetSearcher().nextAssetNumber());
			String startpart = PathUtilities.extractPagePath(assetsourcepath);
			startpart = startpart + "_" + asset.getId();
			String type = PathUtilities.extractPageType(assetsourcepath);
			if( type == null )
			{
				assetsourcepath = startpart;
			}
			else
			{
				assetsourcepath = startpart + "." + type;
			}
			
		}
		asset.setSourcePath(assetsourcepath);

		
		Page dest = getPageManager().getPage("/WEB-INF/data/" + archive.getCatalogId() + "/originals/" + assetsourcepath);
		if(!inPage.exists()){
			log.info("Could not find uploaded file: " + inPage.getPath());
		}
		if(inPage.exists() && !inPage.getPath().equals(dest.getPath()))//move from tmp location to final location
		{
			getPageManager().movePage(inPage, dest);
		}
		
		asset = getAssetImporter().getAssetUtilities().populateAsset(asset, dest.getContentItem(), archive, assetsourcepath, inReq.getUser());
		for (Iterator iterator = vals.keySet().iterator(); iterator.hasNext();)
		{
			String field  = (String)iterator.next();
			String val = (String)vals.get(field);
			asset.setProperty(field, val);
		}
		for (Iterator iterator = cats.iterator(); iterator.hasNext();)
		{
			Category cat = (Category) iterator.next();
			asset.addCategory(cat);
		}
		asset.setProperty("editstatus","1");
		//asset.setProperty("importstatus", "uploading");
		asset.setProperty("importstatus", "imported");
		asset.setProperty("previewtatus", "0");
		asset.setProperty("owner", inReq.getUserName());
		asset.setProperty("datatype", "original");
		asset.setProperty("assetaddeddate", DateStorageUtil.getStorageUtil().formatForStorage(new Date()));
		
		if( asset.get("assettype") == null)
		{
			Data type = archive.getDefaultAssetTypeForFile(asset.getName());
			if( type != null)
			{
				asset.setProperty("assettype", type.getId());
			}
		}
		
		output.add(asset);
		
	}
	public void checkHasPrimary(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		Asset target = getAsset(inReq);
		if(target.getPrimaryFile() == null){
			String destination = "/WEB-INF/data" + archive.getCatalogHome() + "/originals/" + target.getSourcePath();
			List paths = getPageManager().getChildrenPaths(destination);
			if(paths.size() > 0){
				for (Iterator iterator = paths.iterator(); iterator.hasNext();) {
					String path = (String) iterator.next();
					if(!path.contains(".versions")){
						Page page = getPageManager().getPage(path);
						target.setPrimaryFile(page.getName());
						removeAssetImages(inReq);
						archive.saveAsset(target, null);
						
						break;
					}
					
					
				}
			}
			
		}
		
	}
	
	
	public void selectPrimaryAsset(WebPageRequest inReq)
	{
		String primaryname = inReq.getRequestParameter("filename");
		String imagefilename = inReq.getRequestParameter("imagefilename");
		String ext = PathUtilities.extractPageType(primaryname);

		MediaArchive archive = getMediaArchive(inReq);
		Asset target = getAsset(inReq);
		
		if (target != null)
		{
			if(ext != null)
			{
				target.setProperty("fileformat", ext);
			}
			if(primaryname != null)
			{
				target.setProperty("primaryfile", PathUtilities.extractFileName(primaryname));
			}
			if(imagefilename != null)
			{
				target.setProperty("imagefile", PathUtilities.extractFileName(imagefilename));
			}
			Page itemFile = archive.getOriginalDocument(target);
			
			// We're going to allow the metadata reader to replace this asset's properties
			// but we want to keep old values the reader is not going to replace
			PropertyDetails details = archive.getAssetSearcher().getPropertyDetails();
			HashMap<String, String> externaldetails = new HashMap<String, String>();
			for(Iterator i = details.iterator(); i.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) i.next();
				if(detail.getExternalId() != null)
				{
					externaldetails.put(detail.getId(), target.get(detail.getId()));
					target.setProperty(detail.getId(), null);
				}
			}
			
			getAssetImporter().getAssetUtilities().getMetaDataReader().populateAsset(archive, new File(itemFile.getContentItem().getAbsolutePath()), target);
			
			for(String detail: externaldetails.keySet())
			{
				if(target.get(detail) == null)
				{
					target.setProperty(detail, externaldetails.get(detail));
				}
			}
			archive.saveAsset(target, inReq.getUser());
			
			//TODO: run queueconversions.grooovy
			
			//generate mediums for videos if necessary
			/*
			String rendertype = archive.getMediaRenderType(target.get("fileformat"));
			if( "video".equals(rendertype) )
			{
				MediaCreator creator = archive.getCreatorManager().getMediaCreatorByOutputFormat("jpg");
				Page thumb = getPageManager().getPage( "/" + archive.getCatalogId() + "/downloads/preview/medium/medium.jpg");
				WebPageRequest req = inReq.copy(thumb);
				String length = target.get("length");
				log.info("video length: " + length);
				if(length == null)
				{
					return;
				}
				int duration = Integer.parseInt(length);
				if(duration == 0)
				{
					return;
				}
				int frames = 10;
				if( duration < 10 )
				{
					frames = duration;
				}
				int interval = (int) Math.round((double)duration / (double)frames);
				log.info("interval " + interval);
				for(int current = 0; current < frames; current ++)
				{
					ConvertInstructions inStructions = creator.createInstructions(req, archive, "jpg", target.getSourcePath());
					inStructions.addProperty("timeoffset", String.valueOf(current*interval));
					inStructions.addProperty("pathpostfix", "mediums/" + current);
					creator.populateOutputPath(archive, inStructions);
					Page output = getPageManager().getPage(inStructions.getOutputPath());
					if( !output.exists() || output.getContentItem().getLength() == 0 )
					{
						output = creator.createOutput(archive,inStructions);
					}
				}
			}
			*/
		}
	}

	public void uploadToDataDirectory(WebPageRequest inReq) throws Exception
	{
		// Why would you want to upload to the produts root directory?

		UploadRequest map = (UploadRequest) inReq.getPageValue("uploadrequest");
		List unzipped = (List) inReq.getPageValue("unzippedfiles");
		// Final destination
		String assetRoot = inReq.findValue("assetrootfolder");

		MediaArchive archive = getMediaArchive(inReq);

		for (Iterator iterator = map.getUploadItems().iterator(); iterator.hasNext();)
		{
			FileUploadItem item = (FileUploadItem) iterator.next();
			String path = item.getSavedPage().getPath();
			if (unzipped != null && unzipped.size() > 0 && path.toLowerCase().endsWith(".zip"))
			{
				continue;
			}
			getAssetImporter().processOn(assetRoot, path,archive, 0,inReq.getUser());
		}

		if (unzipped != null)
		{
			for (Iterator iterator = unzipped.iterator(); iterator.hasNext();)
			{
				Page page = (Page) iterator.next();
				getAssetImporter().processOn(assetRoot, page.getPath(), archive, 0,inReq.getUser());
			}
		}
	}

	public void checkImports(WebPageRequest inReq) throws Exception
	{
		// Why would you want to upload to the produts root directory?
		UploadRequest map = (UploadRequest) inReq.getPageValue("uploadrequest");
		List unzipped = (List) inReq.getPageValue("unzippedfiles");
		// Final destination
		String assetRoot = inReq.findValue("assetrootfolder");

		MediaArchive archive = getMediaArchive(inReq);

		for (Iterator iterator = map.getUploadItems().iterator(); iterator.hasNext();)
		{
			FileUploadItem item = (FileUploadItem) iterator.next();
			String path = item.getSavedPage().getPath();
			if (unzipped != null && unzipped.size() > 0 && path.toLowerCase().endsWith(".zip"))
			{
				continue;
			}
			getAssetImporter().processOn(assetRoot, path, archive,0, inReq.getUser());
		}

		if (unzipped != null)
		{
			for (Iterator iterator = unzipped.iterator(); iterator.hasNext();)
			{
				Page page = (Page) iterator.next();
				getAssetImporter().processOn(assetRoot, page.getPath(), archive,0,inReq.getUser());
			}
		}
	}

	
	public Page getAssetsPage(MediaArchive inArchive, String inSourcePath)
	{

		String prefix = inArchive.getCatalogHome() + "/assets/";
		String path = prefix + inSourcePath;
		Page page = getPageManager().getPage(path);
		return page;
	}
	/**
	 * @deprecated use Import Hot Folder script?
	 * @param inReq
	 */
	public void importFromOriginals(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		String assetRoot = "/WEB-INF/data/" + archive.getCatalogId() + "/originals/";
		
		importAndSearch(inReq, archive, assetRoot, assetRoot);
		
	}	
	public void runAutoMountImport(WebPageRequest inReq)
	{
		//filterout
		String catid = inReq.getRequestParameter("importcatalog");
		String path = inReq.getRequestParameter("importpath");
		if(path.endsWith("\\"))
		{
			path = path.substring(0, path.length()-1);
		}
		String foldername = null;
		String os = System.getProperty("os.name").toLowerCase();
		if(os.contains("windows"))
		{
			foldername = path.substring(path.lastIndexOf("\\")+1);
		}
		else
		{
			foldername = path.substring(path.lastIndexOf("/") + 1);
		}
		String mountpath = "/WEB-INF/data/" + catid +"/originals/"+ foldername;
		Repository repo = getPageManager().getRepositoryManager().getRepository(mountpath);
		boolean addnew = false;
		if(repo == null)
		{
			repo = new FileRepository();
			addnew = true;
		}		
		String filter = inReq.findValue("filterin");
		String filterout = inReq.findValue("filterout");
		repo.setExternalPath(path);
		repo.setPath(mountpath);
		repo.setFilterOut(filterout);
		repo.setFilterIn(filter);
		if( addnew )
		{
			getPageManager().getRepositoryManager().addRepository(repo);
		}
		getWebServer().saveMounts(getPageManager().getRepositoryManager().getRepositories());
		
		String assetRoot = "/WEB-INF/data/" + catid + "/originals/";
		MediaArchive archive = getMediaArchive(inReq);

		importAndSearch(inReq, archive, mountpath, assetRoot);
	}
	protected void importAndSearch(WebPageRequest inReq, MediaArchive inArchive, String mountpath, String assetRoot)
	{
		List<String> created = getAssetImporter().processOn(assetRoot, assetRoot, inArchive,0,inReq.getUser());
		
		SearchQuery search = inArchive.getAssetSearcher().createSearchQuery();
		int max = Math.min(10000, created.size());
		for (int i = 0; i < max; i++)
		{
			search.addMatches("id",created.get(i));
		}
		HitTracker lht = inArchive.getAssetSearcher().cachedSearch(inReq, search);
		lht.getSearchQuery().setResultType("search");
		lht.getSearchQuery().setHitsName("hits");
		inReq.putPageValue(lht.getHitsName(), lht);
		inReq.putSessionValue(lht.getSessionId(), lht);
		inReq.putPageValue("catalogid", inArchive.getCatalogId());
		inReq.putPageValue("numrecords", new Integer(created.size()));

		
	}
	
//	public void runImport(WebPageRequest inReq)
//	{
//		MediaArchive archive = getMediaArchive(inReq);
//		String sourcePath = inReq.getRequestParameter("sourcepath");
//		if (sourcePath == null || sourcePath.length() == 0)
//		{
//			sourcePath = "/";
//		}
//		Page dir = getAssetsPage(archive, sourcePath);
//		String importType = dir.getProperty("importtype");
//		
//		String mounts = dir.getProperty("mountsonly");
//		boolean mountsonly = false;
//		if( mounts != null )
//		{
//			mountsonly = Boolean.parseBoolean(mounts);
//		}
//		
//		String assetRoot = archive.getCatalogHome() + "/data/originals/";
//
//		String start = assetRoot;
//		if (sourcePath.length() > 1)
//		{
//			start = start + sourcePath;
//		}
//		int deletecount = 0;
//		List created = null;
//		if ("imageimportconvert".equals(importType))
//		{
//			// Are we creating a new folder?
//			String newFolder = inReq.getRequestParameter("newname");
//			if (newFolder != null)
//			{
//				start = start + newFolder;
//			}
//			// delete all the old assets
//			AssetSearcher searcher = archive.getAssetSearcher();
//			SearchQuery q = searcher.createSearchQuery();
//			q.addStartsWith("sourcepath", sourcePath);
//			HitTracker old = searcher.search(q);
//			searcher.deleteFromIndex(old);
//			String importFilters = inReq.getRequestParameter("importextensions");
//			created = getAssetImporter().processOn(assetRoot, start, importFilters, archive, mountsonly, inReq.getUser());
//
//			// Delete old assets not present anymore
//			for (Iterator iterator = old.iterator(); iterator.hasNext();)
//			{
//				Document hit = (Document) iterator.next();
//				Asset p = archive.getAssetBySourcePath(hit.get("sourcepath"));
//				if (p != null)
//				{
//					SearchQuery sq = searcher.createSearchQuery();
//					sq.addMatches("sourcepath", hit.get("sourcepath"));
//					HitTracker tracker = searcher.search(sq);
//					if (tracker.size() == 0)
//					{
//						// Asset is not in the index anymore...delete it from
//						// disk
//						archive.removeGeneratedImages(p);
//						archive.getAssetSearcher().delete(p, inReq.getUser());
//						deletecount++;
//					}
//					
//				}
//			}
//		}
//		ListHitTracker lht = new ListHitTracker(created);
//		lht.getSearchQuery().setHitsName("hits");
//		lht.getSearchQuery().setCatalogId(archive.getCatalogId());
//		
//		inReq.putSessionValue(lht.getSessionId(), lht);
//		inReq.putPageValue(lht.getHitsName(), lht);
//		
//		inReq.putPageValue("numdeletes", deletecount);
//		inReq.putPageValue("numrecords", new Integer(created.size()));
//	}
	
	public void removeExpiredAssets(WebPageRequest inReq)
	{
		String sourcepath = inReq.getRequestParameter("sourcepath");
		MediaArchive archive = getMediaArchive(inReq);
		List removed =getAssetImporter().removeExpiredAssets(archive, sourcepath, inReq.getUser());
		inReq.putPageValue("removedassets", removed);
	}

	public AssetImporter getAssetImporter()
	{
		return fieldAssetAssetImporter;
	}

	public void setAssetImporter(AssetImporter inAssetAssetImporter)
	{
		fieldAssetAssetImporter = inAssetAssetImporter;
	}

	public FileUpload getFileUpload()
	{
		return fieldFileUpload;
	}

	public void setFileUpload(FileUpload inFileUpload)
	{
		fieldFileUpload = inFileUpload;
	}

	public void redirectToGallery(WebPageRequest inReq)
	{
		MediaArchive store = getMediaArchive(inReq);

		String include = inReq.findValue("includefilter");
		int index = include.lastIndexOf("/");
		String dir = include.substring(0, index);

		String redirTo = "/" + store.getCatalogId() + "/layout/assets/files/index.html?path=" + dir;
		inReq.redirectPermanently(redirTo);
	}
	/**
	 * @param inReq
	public void encodeVideos(WebPageRequest inReq)
	{
		String hitsname = inReq.findValue("hitsname");
		if (hitsname == null)
		{
			hitsname = "hits";
		}
		HitTracker tracker = (HitTracker) inReq.getPageValue(hitsname);
		if (tracker == null)
		{
			return;
		}

		final MediaArchive archive = getMediaArchive(inReq);

		//queue up some video encoding. We should do this some other way
		
		for (Iterator iterator = tracker.iterator(); iterator.hasNext();)
		{
			Object object = (Object) iterator.next();
			if (!(object instanceof Asset))
			{
				continue;
			}
			final Asset asset = (Asset) object;
			String render = archive.getMediaRenderType(asset.getFileFormat());
			if (!render.equals("video"))
			{
				continue;
			}
			Runnable runner = new Runnable()
			{
				public void run()
				{
					try
					{
						ConvertInstructions instructions = new ConvertInstructions();
						instructions.setForce(true);
						instructions.setInputExtension( asset.getFileFormat() );
						instructions.setOutputExtension("flv");
						instructions.setAssetSourcePath(asset.getSourcePath());
						archive.getCreatorManager().createOutput( instructions );
					}
					catch(Exception e)
					{
						log.error("Couldn't convert video", e);
					}
				}
			};
//			BaseTask task = new BaseTask();
//			task.addAction(new RunnableAction(runner) );
//			getTaskManager().addTask(task);
		}
	}
*/
	public WebEventListener getWebEventListener()
	{
		return fieldWebEventListener;
	}
	
	public void setWebEventListener( WebEventListener inWebEventListener)
	{
		fieldWebEventListener = inWebEventListener;
	}
	
	protected Data buildDataObject( WebPageRequest inReq, PropertyDetails inDetails )
	{
		Data data = new BaseData();
		for (Iterator i = inDetails.iterator(); i.hasNext();) {
			PropertyDetail detail = (PropertyDetail) i.next();
			String value = inReq.getRequestParameter(detail.getId());
			if (value != null)
			{
				data.setProperty(detail.getId(), value);
			}
		}
		return data;
	}
	
	public void saveUsageHistory( WebPageRequest inReq )
	{
		MediaArchive archive = getMediaArchive(inReq);
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(), "usagehistory");
		String[] assetids = inReq.getRequestParameters("assetid");
		ArrayList<Data> newrecords = new ArrayList<Data>();
		
		if(assetids == null)
		{
			return;
		}
		
		for(String assetid : assetids)
		{
			Data data = buildDataObject(inReq, searcher.getPropertyDetails());
			PropertyDetail detail = searcher.getDetail("date");
			if (detail != null)
			{
				data.setProperty(detail.getId(), DateStorageUtil.getStorageUtil().formatForStorage(new Date()));
			}
			detail = searcher.getDetail("user");
			if (detail != null)
			{
				data.setProperty(detail.getId(), inReq.getUserName());
			}
			detail = searcher.getDetail("assetid");
			if(detail != null)
			{
				data.setProperty(detail.getId(), assetid);
			}
			Asset asset = archive.getAsset(assetid);
			data.setSourcePath(asset.getSourcePath());
			searcher.saveData(data, inReq.getUser());
			newrecords.add(data);
		}
		inReq.putPageValue("newrecord", newrecords);
	}
	
	public void loadUsageHistory( WebPageRequest inReq )
	{
		MediaArchive archive = getMediaArchive(inReq);
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(), "usagehistory");

		String assetid = inReq.getRequestParameter("assetid");
		if( assetid == null)
		{
			Asset asset = getAsset(inReq);
			if( asset == null)
			{
				return;
			}
			assetid = asset.getId();
		}
		
		if(assetid == null)
		{
			return;
		}
		HitTracker hits = searcher.fieldSearch("assetid", assetid );
		inReq.putPageValue("history", hits);
		inReq.putPageValue("historySearcher", searcher);
	}
	
	public void saveSelectionProperties( WebPageRequest inReq )
	{
		MediaArchive archive = getMediaArchive(inReq);
		String[] assetids = inReq.getRequestParameters("assetselect_" + archive.getCatalogId());
		String[] fields = inReq.getRequestParameters("field");
		for(int i = 0; i < assetids.length; i++)
		{
			Asset asset = archive.getAsset(assetids[i]);
			if(asset == null)
			{
				continue;
			}
			for(int j = 0; j < fields.length; j++)
			{
				String value = inReq.getRequestParameter(asset.getId() + "." + fields[j] + ".value");
				if (value == null)
				{
					value = inReq.getRequestParameter(fields[j] + ".value");
				}
				asset.setProperty(fields[j], value);
				archive.getAssetSearcher().saveData(asset, inReq.getUser());
			}
		}
	}
	public void fireAssetEvent( WebPageRequest inReq)
	{
		String type  = inReq.getPageProperty("asseteventtype");
		MediaArchive archive = getMediaArchive(inReq);
		Asset asset = getAsset(inReq);
		archive.fireMediaEvent("asset/" + type, inReq.getUser(), asset);
		
	}
	public WebServer getWebServer()
	{
		return fieldWebServer;
	}

	public void setWebServer(WebServer inWebServer)
	{
		fieldWebServer = inWebServer;
	}
	
	public void resizedAsset(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		String width = (String)inReq.getRequestParameter("width");
		String height = (String)inReq.getRequestParameter("height");
		String scalex = (String)inReq.getRequestParameter("scalex");
		String scaley = (String)inReq.getRequestParameter("scaley");
		
		Asset asset = getAsset(inReq);
		if(scalex == null && scaley == null)
		{
			asset.setProperty("width", width);
			asset.setProperty("height", height);
		}
		else
		{
			float fscalex = Float.parseFloat(scalex);
			float fscaley = Float.parseFloat(scaley);
			int iwidth = Integer.parseInt(width);
			int iheight = Integer.parseInt(height);
			if(fscalex > 0)
			{
				asset.setProperty("width", Integer.toString((int)(iwidth * fscalex)));
			}
			if(fscaley > 0)
			{
				asset.setProperty("height", Integer.toString((int)(iheight * fscaley)));
			}
		}
		archive.saveAsset(asset, inReq.getUser());
		archive.removeGeneratedImages(asset);
	}
	
	
	public void loadAssetVotes(WebPageRequest inReq) throws Exception
	{
		Asset asset = (Asset)inReq.getPageValue("asset");
		if( asset == null)
		{
			return;
		}
		Searcher searcher = getSearcherManager().getSearcher(asset.getCatalogId(), "assetvotes");
		if (searcher == null)
		{
			throw new OpenEditException("Unable to load searcher for assetvotes.");
		}
		SearchQuery q = searcher.createSearchQuery();
		q.setHitsName("voteshits");
		q.addMatches("assetid", asset.getId());
		HitTracker hits = searcher.cachedSearch(inReq, q);

		if (inReq.getUser() != null)
		{
			for (Object hit : hits)
			{
				if (hits.getValue(hit, "username").equals(inReq.getUserName()))
				{
					inReq.putPageValue("alreadyvoted", Boolean.TRUE);
					break;
				}
			}
		}
		int count = asset.getInt("assetvotes");
		if( count != hits.size())
		{
			asset.setProperty("assetvotes", String.valueOf( hits.size()) );
			MediaArchive archive = getMediaArchive(inReq);
			archive.fireMediaEvent("asset/assetsave", inReq.getUser(), asset);
		}
		
	}

	public void deleteAssetVote(WebPageRequest ex)
	{
		Asset asset = getAsset(ex);
		User user = ex.getUser();

		MediaArchive archive = getMediaArchive(ex);
		removeVote(asset, archive, user);
	}
	public void removeVote(Asset asset, MediaArchive archive, User user)
	{
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(), "assetvotes");
		Data row = (Data)searcher.searchById(user.getUserName() + "_" + asset.getId());
		if( row != null)
		{
			searcher.delete(row, user);
		}
	}

	public void voteForAsset(WebPageRequest ex) throws Exception
	{
/*		#set($searcher = $searcherManager.getSearcher($catalogid, "assetvotes"))
		#set($dateformat = $searcher.getDetail("time").getDateFormat())
		#set($date = $dateformat.format($today))
		#set($alreadyVoted = $uservote)
		$context.putPageValue("votetoremove", $uservote)
*/
		MediaArchive archive = getMediaArchive(ex);

		Asset asset = getAsset(ex);

		voteForAsset(asset,  archive, ex.getUser());
	}
	public void voteForAsset(Asset asset, MediaArchive archive, User inUser)
	{
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(), "assetvotes");
	//	DateFormat dateformat = searcher.getDetail("time").getDateFormat();
		Data row = searcher.createNewData();
		String username = inUser.getUserName();
		
		row.setId(username + "_" + asset.getId());
		String date = DateStorageUtil.getStorageUtil().formatForStorage(new Date());
		row.setProperty("time", date);
		row.setProperty("assetid", asset.getId());
		row.setProperty("username", inUser.getUserName());
		row.setSourcePath(asset.getSourcePath());
		searcher.saveData(row,inUser);
		archive.fireMediaEvent("asset/userlikes", inUser, asset);
		//archive.getAssetSearcher().updateIndex(asset); //get the rank updated
	}
	
	public void saveAssetData(WebPageRequest inReq) throws Exception
	{
		Asset asset = getAsset(inReq);
		getMediaArchive(inReq).saveAsset(asset, inReq.getUser());
	}
	
	public void markAssetsDeleted(WebPageRequest inReq) throws OpenEditException 
	{
		String[] sourcepath = inReq.getRequestParameters("sourcepath");
		if( sourcepath != null)
		{
			MediaArchive archive = getMediaArchive(inReq);
			List assets = new ArrayList();
			for (int i = 0; i < sourcepath.length; i++)
			{
				Asset asset = archive.getAssetBySourcePath(sourcepath[i]);
				if( asset != null)
				{
					asset.setProperty("editstatus","7");
					assets.add(asset);
				}
			}
			String deleterecord = inReq.getRequestParameter("deleterecord");
			if( Boolean.parseBoolean(deleterecord))
			{
				archive.saveAssets(assets, inReq.getUser());
				for (Iterator iterator = assets.iterator(); iterator.hasNext();)
				{
					Asset asset = (Asset) iterator.next();
					archive.getAssetSearcher().delete(asset, inReq.getUser());
				}
			}
			else
			{
				archive.saveAssets(assets, inReq.getUser());
			}
			inReq.putPageValue("deletedlist",assets);
		}
	}
	public Data createMultiEditDataFromSelections(WebPageRequest inReq) throws Exception
	{
		String hitsname = inReq.getRequestParameter("hitssessionid");//expects session id
		if( hitsname == null)
		{
			return null;
		}
		MediaArchive store = getMediaArchive(inReq);
		HitTracker hits = (HitTracker) inReq.getSessionValue(hitsname);
		if( hits == null)
		{
			log.error("Could not find " + hitsname);
			return null;
		}
		CompositeAsset composite = new CompositeAsset();
		for (Iterator iterator = hits.getSelectedHits().iterator(); iterator.hasNext();)
		{
			Object target = (Object) iterator.next();
			Asset p = null;
			if( target instanceof Asset)
			{
				p = (Asset)target;
			}
			else
			{
				String id = hits.getValue(target, "id");
				p = store.getAsset(id);
			}
			if( p != null)
			{
				composite.addData(p);
			}
		}
		composite.setId("multiedit:"+hitsname);
		//set request param?
		inReq.setRequestParameter("assetid",composite.getId());
		inReq.putPageValue("data", composite);
		inReq.putPageValue("asset", composite);
		inReq.putSessionValue(composite.getId(), composite);
		
		return composite;
	}
}
