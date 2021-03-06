package org.openedit.entermedia.data;

import java.io.File;

import org.openedit.entermedia.Asset;
import org.openedit.entermedia.BaseEnterMediaTest;
import org.openedit.entermedia.MediaArchive;
import org.openedit.entermedia.scanner.ExiftoolMetadataExtractor;
import org.openedit.entermedia.xmp.XmpWriter;
import org.openedit.repository.filesystem.FileItem;

public class XmpTest extends BaseEnterMediaTest{
	
	public void testXmpWriting() throws Exception
	{
		Asset asset = new Asset();
		asset.addKeyword("test1");
		asset.addKeyword("test2");
		asset.setSourcePath("testassets/Indesign.indd");
		File assetfile = new File(getRoot(), "../etc/testassets/Indesign.indd");

		XmpWriter writer = (XmpWriter) getBean("xmpWriter");
		assertNotNull(writer);
		writer.saveMetadata(getMediaArchive(), asset);
		
		Asset newasset = new Asset();
		
		ExiftoolMetadataExtractor reader= (ExiftoolMetadataExtractor)getBean("exiftoolMetadataExtractor");
		MediaArchive mediaArchive = getMediaArchive();
		
		FileItem item = new FileItem();
		item.setPath("/etc/testassets/Indesign.indd");
		item.setFile(assetfile);
		
		reader.extractData(mediaArchive, item, newasset);
	//	assertEquals(2, newasset.getKeywords().size());
		assertTrue(newasset.getKeywords().contains("test1"));
		assertTrue(newasset.getKeywords().contains("test2"));
	}
	

}
