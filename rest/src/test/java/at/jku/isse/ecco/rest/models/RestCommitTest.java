package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RestCommitTest {

    @Test
    public void delegatesSimpleGettersToTheWrappedCommit() {
        Commit commit = mock(Commit.class);
        when(commit.getId()).thenReturn("commit-id");
        when(commit.getCommitMessage()).thenReturn("fixed the bug");
        when(commit.getUsername()).thenReturn("alice");
        when(commit.getConfiguration()).thenReturn(mock(Configuration.class));
        when(commit.getAssociations()).thenReturn(List.of());

        RestCommit restCommit = new RestCommit(commit);

        assertEquals("commit-id", restCommit.getId());
        assertEquals("fixed the bug", restCommit.getCommitMessage());
        assertEquals("alice", restCommit.getUsername());
    }

    /**
     * In 24-hour time: the pattern had hh, the hour of a 12-hour clock without AM or PM, so a commit
     * at 22:10 showed as 10:10.
     */
    @Test
    public void formatsTheDateAsYyyyMmDdHhMmSsInTwentyFourHourTime() {
        Commit commit = mock(Commit.class);
        Date date = Date.from(LocalDateTime.of(2026, 11, 1, 22, 10, 11).atZone(ZoneId.systemDefault()).toInstant());
        when(commit.getDate()).thenReturn(date);

        RestCommit restCommit = new RestCommit(commit);

        assertEquals("2026-11-01 22:10:11", restCommit.getDate());
    }

    @Test
    public void wrapsTheConfiguration() {
        Commit commit = mock(Commit.class);
        Configuration configuration = mock(Configuration.class);
        when(configuration.getFeatureRevisions()).thenReturn(new FeatureRevision[0]);
        when(commit.getConfiguration()).thenReturn(configuration);

        RestCommit restCommit = new RestCommit(commit);

        assertNotNull(restCommit.getConfiguration());
        assertTrue(restCommit.getConfiguration().getFeatureRevisions().isEmpty());
    }

    @Test
    public void wrapsEachAssociationIndividually() {
        Commit commit = mock(Commit.class);
        Association associationA = mock(Association.class);
        Association associationB = mock(Association.class);
        when(associationA.getId()).thenReturn("assoc-a");
        when(associationB.getId()).thenReturn("assoc-b");
        when(commit.getAssociations()).thenReturn(List.of(associationA, associationB));

        RestCommit restCommit = new RestCommit(commit);

        List<String> ids = restCommit.getAssociations().stream().map(RestAssociation::getId).toList();
        assertEquals(List.of("assoc-a", "assoc-b"), ids);
    }

    @Test
    public void getAssociationsOfCommitWithNoAssociationsIsEmpty() {
        Commit commit = mock(Commit.class);
        when(commit.getAssociations()).thenReturn(List.of());

        RestCommit restCommit = new RestCommit(commit);

        assertTrue(restCommit.getAssociations().isEmpty());
    }
}
