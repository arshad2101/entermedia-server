package modules.projects;

import model.projects.ProjectManager
import model.projects.UserCollection

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.data.SearcherManager
import org.openedit.entermedia.Asset
import org.openedit.entermedia.MediaArchive
import org.openedit.profile.UserProfile

import com.openedit.WebPageRequest
import com.openedit.hittracker.FilterNode
import com.openedit.hittracker.HitTracker
import com.openedit.users.*

public class BaseProjectManager implements ProjectManager
{
	private static final Log log = LogFactory.getLog(BaseProjectManager.class);
	
	protected SearcherManager fieldSearcherManager;
	protected String fieldCatalogId;
	/* (non-Javadoc)
	 * @see model.projects.ProjectManager#getCatalogId()
	 */
	@Override
	public String getCatalogId()
	{
		return fieldCatalogId;
	}
	
	/* (non-Javadoc)
	 * @see model.projects.ProjectManager#setCatalogId(java.lang.String)
	 */
	@Override
	public void setCatalogId(String inCatId)
	{
		fieldCatalogId = inCatId;
	}
	
	
	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}

	/* (non-Javadoc)
	 * @see org.openedit.entermedia.push.PushManager#setSearcherManager(org.openedit.data.SearcherManager)
	 */
	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}
	
	protected Data getCurrentLibrary( UserProfile inProfile)
	{
		if( inProfile != null)
		{
			String lastselectedid = inProfile.get("last_selected_library" );
			if( lastselectedid != null)
			{
				Data library = getSearcherManager().getData(getCatalogId(), "library", lastselectedid );
				return library;
			}
		}
		return null;
	}
	
	public Collection<UserCollection> loadCollections(WebPageRequest inReq)
	{
		//get a library
		Data library = getCurrentLibrary(inReq.getUserProfile());
		if( library != null)
		{
			inReq.putPageValue("selectedlibrary",library);
			
			Searcher assetsearcher = getSearcherManager().getSearcher(getCatalogId(),"asset");
			
			HitTracker hits = assetsearcher.query().match("libraries",library.getId()).named("sidebar").search(inReq);
			if(hits != null)
			{
				int assetsize = hits.size();
				inReq.putPageValue("librarysize",assetsize);
			}
			Searcher searcher = getSearcherManager().getSearcher(getCatalogId(),"librarycollection");
			HitTracker allcollections = searcher.query().exact("library",library.getId()).sort("name").named("sidebar").search(inReq);

			//Show all the collections for a library
			inReq.putPageValue("allcollections", allcollections);
			
			//enable filters to show the asset count on each collection node
			FilterNode collectionhits = null;
			if( allcollections.size() > 0 ) //May not have any collections
			{
				Searcher collectionassetsearcher = getSearcherManager().getSearcher(getCatalogId(),"librarycollectionasset");
				
				//Build list of ID's
				List ids = new ArrayList(allcollections.size());
				for (Iterator iterator = allcollections.iterator(); iterator.hasNext();)
				{
					Data collection = (Data) iterator.next();
					ids.add( collection.getId() );
				}
				if(ids.size() > 0)
				{
					HitTracker collectionassets = collectionassetsearcher.query().orgroup("librarycollection",ids).named("sidebar").search(inReq); //todo: Cache?
					if(collectionassets != null && collectionassets.size() > 0) //No assets found at all
					{
						collectionhits = collectionassets.findFilterNode("librarycollection");
					}
				}
			}			
			Collection<UserCollection> usercollections = loadUserCollections(allcollections, collectionhits);
			inReq.putPageValue("usercollections", usercollections);
			return usercollections;
		}
		return Collections.EMPTY_LIST;
				
		//search
		//Searcher searcher = getSearcherManager().getSearcher(getMediaArchive().getCatalogId(),"librarycollection") )
		//HitTracker labels = searcher.query().match("library",$library.getId()).sort("name").search() )
		
	}
	protected Collection<UserCollection> loadUserCollections(Collection<Data> allcollections, FilterNode collectionhits)
	{
		List usercollections = new ArrayList(allcollections.size());
		for (Data collection : allcollections)
		{
			UserCollection uc = new UserCollection();
			uc.setCollection(collection);
			if( collectionhits != null)
			{
				int assetcount = collectionhits.getCount(collection.getId());
				uc.setAssetCount(assetcount);
			}
			usercollections.add(uc);
		}
		 
		return usercollections;
	}
	public void addAssetToCollection(WebPageRequest inReq, MediaArchive archive, String libraryid, Collection<Data> assets)
	{
		addAssetToLibrary(inReq, archive, libraryid, assets);
	
		String librarycollection = inReq.getRequestParameter("librarycollection");
		Searcher librarycollectionassetSearcher = archive.getSearcher("librarycollectionasset");
	
		List tosave = new ArrayList();
		for(Data asset : assets)
		{
			Data found = librarycollectionassetSearcher.query().match("librarycollection", librarycollection).match("asset", asset.getId()).searchOne();
		
			if (found == null)
			{
				found = librarycollectionassetSearcher.createNewData();
				found.setSourcePath(libraryid + "/" + librarycollection);
				found.setProperty("librarycollection", librarycollection);
				found.setProperty("asset", asset.getId());
				tosave.add(found);
				if( tosave.size() > 200)
				{
					for(Data save : tosave)
					{
						librarycollectionassetSearcher.saveData(save, inReq.getUser());
					}
					tosave.clear();
				}
				//log.info("Saved " + found.getId());
			}
		}
		for(Data found : tosave)
		{
			librarycollectionassetSearcher.saveData(found, inReq.getUser());
		}
	}
	public void addAssetToCollection(WebPageRequest inReq, MediaArchive archive, String libraryid, String assetid)
	{
		addAssetToLibrary(inReq, archive, libraryid, assetid);
	
		String librarycollection = inReq.getRequestParameter("librarycollection");
		Searcher librarycollectionassetSearcher = archive.getSearcher("librarycollectionasset");
	
		Data found = librarycollectionassetSearcher.query().match("librarycollection", librarycollection).match("asset", assetid).searchOne();
	
		if (found == null)
		{
			found = librarycollectionassetSearcher.createNewData();
			found.setSourcePath(libraryid + "/" + librarycollection);
			found.setProperty("librarycollection", librarycollection);
			found.setProperty("asset", assetid);
			librarycollectionassetSearcher.saveData(found, inReq.getUser());
			log.info("Saved " + found.getId());
		}
	}
	public void addAssetToLibrary(WebPageRequest inReq, MediaArchive archive, String libraryid, Collection<Data> assets)
	{
		List tosave = new ArrayList();
		for(Data toadd: assets)
		{
			Asset asset = archive.getAssetSearcher().loadData(toadd)
		
			if (asset != null && !asset.getLibraries().contains(libraryid))
			{
				asset.addLibrary(libraryid);
				tosave.add(asset);
				if( tosave.size() > 500)
				{
					for(Data save : tosave)
					{
						archive.saveAsset(save, inReq.getUser());
					}
					tosave.clear();
				}
			}
		}
		for(Data asset : tosave)
		{
			archive.saveAsset(asset, inReq.getUser());
		}
	}

	public void addAssetToLibrary(WebPageRequest inReq, MediaArchive archive, String libraryid, String assetid)
	{
		Asset asset = archive.getAsset(assetid);
	
		if (asset != null && !asset.getLibraries().contains(libraryid))
		{
			asset.addLibrary(libraryid);
			archive.saveAsset(asset, inReq.getUser());	
		}
	}

	public Collection<String> loadAssetsInCollection(WebPageRequest inReq, MediaArchive archive, String inCollectionId)
	{
		Searcher librarycollectionassetSearcher = archive.getSearcher("librarycollectionasset");
		HitTracker found = librarycollectionassetSearcher.query().match("librarycollection", inCollectionId).search(inReq);
		Collection<String> ids = new ArrayList();
		for(Data hit in found)
		{
			String id = hit.get("asset");
			if( id != null)
			{
				ids.add(id);
			}
		}
		return ids;
	}
	public boolean addUserToLibrary( MediaArchive archive, Data inLibrary, User inUser) 
	{
		Searcher searcher = archive.getSearcher("libraryusers");
		Data found = searcher.query().match("userid",inUser.getId()).match("libraryid", inLibrary.getId()).searchOne();	
		if( found == null )
		{
			found = searcher.createNewData();
			found.setProperty("userid",inUser.getId());
			found.setProperty("libraryid",inLibrary.getId());
			searcher.saveData(found,null);
			return true;
		}				
		return false;	
	}

	@Override
	public void removeAssetFromLibrary(WebPageRequest inReq, MediaArchive inArchive, String inLibraryid, Collection<Data> inAssets)
	{
		Searcher librarycollectionsearcher = inArchive.getSearcher("librarycollection");
		HitTracker collections = librarycollectionsearcher.query().match("library",inLibraryid).search();
		for(Data collection: collections)
		{
			removeAssetFromCollection(inReq,inArchive,collection.getId(),inAssets);
		}
		for(Data toadd: inAssets)
		{
			Asset asset = inArchive.getAssetSearcher().loadData(toadd)
		
			if (asset != null && asset.getLibraries().contains(inLibraryid))
			{
				asset.removeLibrary(inLibraryid);
				inArchive.saveAsset(asset, inReq.getUser());
			}
		}
	}

	@Override
	public void removeAssetFromCollection(WebPageRequest inReq, MediaArchive inArchive, String inCollectionid, Collection<Data> inAssets)
	{
			Searcher librarycollectionassetSearcher = inArchive.getSearcher("librarycollectionasset");
		
			List tosave = new ArrayList();
			for(Data asset : inAssets)
			{
				Data found = librarycollectionassetSearcher.query().match("librarycollection", inCollectionid).match("asset", asset.getId()).searchOne();
			
				if (found != null)
				{
					librarycollectionassetSearcher.delete(found,null);
				}
			}
	}
	public Collection<UserCollection> loadRecentCollections(WebPageRequest inReq)
	{
		//enable filters to show the asset count on each collection node
		UserProfile profile = inReq.getUserProfile();
		Searcher librarycollectionsearcher = getSearcherManager().getSearcher(getCatalogId(),"librarycollection");
		HitTracker allcollections = librarycollectionsearcher.query().orgroup("library",profile.getCombinedLibraries()).sort("name").named("sidebar").search(inReq);
		FilterNode collectionhits = null;
		if( allcollections.size() > 0 ) //May not have any collections
		{
			Searcher collectionassetsearcher = getSearcherManager().getSearcher(getCatalogId(),"librarycollectionasset");
			
			//Build list of ID's
			List ids = new ArrayList(allcollections.size());
			for (Iterator iterator = allcollections.iterator(); iterator.hasNext();)
			{
				Data collection = (Data) iterator.next();
				ids.add( collection.getId() );
			}
			if(ids.size() > 0)
			{
				HitTracker collectionassets = collectionassetsearcher.query().orgroup("librarycollection",ids).sort("recorddate").named("homecollections").search(inReq); 
				if(collectionassets != null && collectionassets.size() > 0) //No assets found at all
				{
					collectionhits = collectionassets.findFilterNode("librarycollection");
				}
			}
		}
		Collection<UserCollection> usercollections = loadUserCollections(allcollections, collectionhits);
		inReq.putPageValue("usercollections", usercollections);
		return usercollections;
	}
	
}

