package com.ezsmb.app.core

import org.junit.Assert.*
import org.junit.Test

class FolderPreviewTest {
    @Test fun selectsAtMostTwoMediaFilesWithImagesFirstAndNaturalOrder() {
        val entries = listOf("video.mp4", "图10.jpg", "图2.jpg", "notes.txt").map { Entry(it, false, 100, 1) }
        assertEquals(listOf("图2.jpg", "图10.jpg"), folderPreviewCandidates(entries).map { it.name })
    }
    @Test fun skipsDirectoriesEmptyFilesAndOversizedImagesButAllowsLargeVideo() {
        val entries = listOf(Entry("nested.jpg", true, 100, 1), Entry("empty.png", false, 0, 1),
            Entry("huge.jpg", false, 13 * 1024 * 1024, 1), Entry("movie.mp4", false, 5L * 1024 * 1024 * 1024, 1))
        assertEquals(listOf("movie.mp4"), folderPreviewCandidates(entries).map { it.name })
        assertTrue(folderPreviewCandidates(emptyList()).isEmpty())
    }
}
