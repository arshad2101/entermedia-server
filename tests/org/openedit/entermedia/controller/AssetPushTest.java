package org.openedit.entermedia.controller;

import org.openedit.Data;
import org.openedit.data.Searcher;
import org.openedit.entermedia.Asset;
import org.openedit.entermedia.BaseEnterMediaTest;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.modules.AssetSyncModule;

import com.openedit.WebPageRequest;

public class AssetPushTest extends BaseEnterMediaTest
{

	public void testPushAsset() throws Exception
	{
		AssetSyncModule mod = (AssetSyncModule)getFixture().getModuleManager().getModule("AssetSyncModule");
		WebPageRequest req = getFixture().createPageRequest("/entermedia/catalogs/testcatalog/events/sync/pushassets.html");

		MediaArchive archive = mod.getMediaArchive(req);
		Asset target = archive.getAssetBySourcePath("users/admin/101");
		Searcher pushsearcher = archive.getSearcherManager().getSearcher("entermedia/catalogs/testcatalog", "pushrequest");
		Data request = pushsearcher.createNewData();
		request.setId("testpush");
		request.setProperty("assetid", target.getId());
		request.setProperty("status", "pending");
		request.setSourcePath(target.getSourcePath());
		request.setProperty("sourcefolder", "test");
		
		Searcher hot =  archive.getSearcherManager().getSearcher("entermedia/catalogs/testcatalog", "hotfolder");
		Data local = (Data) hot.searchById("test");
		if(local == null){
			local = hot.createNewData();
		}
		local.setId("test");
		local.setProperty("auto", "true");
		request.setProperty("hotfolder", "test");
		local.setProperty("convertpreset", "original largeimage thumbimage");
		hot.saveData(local, null);
		
		pushsearcher.saveData(request, null);
	
		getFixture().getEngine().executePageActions(req);
		getFixture().getEngine().executePathActions(req);
		
		
		request = (Data) pushsearcher.searchById("testpush");
		assertEquals("status", "complete");
		
		
	}

	

}
