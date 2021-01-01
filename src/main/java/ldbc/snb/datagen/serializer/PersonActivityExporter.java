/* 
 Copyright (c) 2013 LDBC
 Linked Data Benchmark Council (http://www.ldbcouncil.org)
 
 This file is part of ldbc_snb_datagen.
 
 ldbc_snb_datagen is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 ldbc_snb_datagen is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with ldbc_snb_datagen.  If not, see <http://www.gnu.org/licenses/>.
 
 Copyright (C) 2011 OpenLink Software <bdsmt@openlinksw.com>
 All Rights Reserved.
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation;  only Version 2 of the License dated
 June 1991.
 
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.*/
package ldbc.snb.datagen.serializer;

import ldbc.snb.datagen.DatagenMode;
import ldbc.snb.datagen.DatagenParams;
import ldbc.snb.datagen.dictionary.Dictionaries;
import ldbc.snb.datagen.entities.Pair;
import ldbc.snb.datagen.entities.PostTree;
import ldbc.snb.datagen.entities.Triplet;
import ldbc.snb.datagen.entities.dynamic.Forum;
import ldbc.snb.datagen.entities.dynamic.messages.Comment;
import ldbc.snb.datagen.entities.dynamic.messages.Photo;
import ldbc.snb.datagen.entities.dynamic.messages.Post;
import ldbc.snb.datagen.entities.dynamic.relations.ForumMembership;
import ldbc.snb.datagen.entities.dynamic.relations.Like;
import ldbc.snb.datagen.entities.dynamic.Activity;
import ldbc.snb.datagen.entities.dynamic.Wall;
import ldbc.snb.datagen.hadoop.writer.HdfsCsvWriter;

import java.io.IOException;
import java.util.List;

import static ldbc.snb.datagen.util.functional.Thunk.wrapException;

public class PersonActivityExporter implements AutoCloseable {
    protected DynamicActivitySerializer<HdfsCsvWriter> dynamicActivitySerializer;
    protected AbstractInsertEventSerializer abstractInsertEventSerializer;
    protected AbstractDeleteEventSerializer abstractDeleteEventSerializer;

    public PersonActivityExporter(DynamicActivitySerializer<HdfsCsvWriter> dynamicActivitySerializer, AbstractInsertEventSerializer abstractInsertEventSerializer, AbstractDeleteEventSerializer abstractDeleteEventSerializer) {
        this.dynamicActivitySerializer = dynamicActivitySerializer;
        this.abstractInsertEventSerializer = abstractInsertEventSerializer;
        this.abstractDeleteEventSerializer = abstractDeleteEventSerializer;
    }

    private void exportPostWall(final Wall<PostTree> wall) {
        wall.inner.forEach(forum -> {
            wrapException(() -> this.export(forum.getValue0()));
            List<ForumMembership> genForumMembership = forum.getValue1();
            genForumMembership.forEach(m -> wrapException(() -> this.export(m)));
            List<PostTree> thread = forum.getValue2();
            thread.forEach(t -> {
                wrapException(() -> this.export(t.getValue0()));
                List<Like> genLike = t.getValue1();
                genLike.forEach(l -> wrapException(() -> this.export(l)));
                List<Pair<Comment, List<Like>>> genComment = t.getValue2();
                genComment.forEach(c -> {
                    wrapException(() -> this.export(c.getValue0()));
                    List<Like> genLike1 = c.getValue1();
                    genLike1.forEach(l -> wrapException(() -> this.export(l)));
                });
            });
        });
    }

    private void exportAlbumWall(final Wall<Pair<Photo, List<Like>>> genAlbums) {
        genAlbums.inner.forEach(forum -> {
            wrapException(() -> this.export(forum.getValue0()));
            List<ForumMembership> genForumMembership = forum.getValue1();
            genForumMembership.forEach(m -> wrapException(() -> this.export(m)));
            List<Pair<Photo, List<Like>>> thread = forum.getValue2();
            thread.forEach(t -> {
                wrapException(() -> this.export(t.getValue0()));
                List<Like> genLike = t.getValue1();
                genLike.forEach(l -> wrapException(() -> this.export(l)));
            });
        });
    }

    public void export(final Activity genActivity) {
        this.exportPostWall(genActivity.wall);
        genActivity.groups.forEach(this::exportPostWall);
        this.exportAlbumWall(genActivity.albums);
    }

    public void export(final Forum forum) throws Exception {

        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(forum);
        } else {
            if ((forum.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (forum.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            forum.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()))) {
                dynamicActivitySerializer.export(forum);
                if (forum.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(forum);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (forum.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && forum.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
            ) {
                dynamicActivitySerializer.export(forum);
            } else if (forum.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (forum.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    forum.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(forum);
                abstractInsertEventSerializer.changePartition();
                if (forum.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(forum);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (forum.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && forum.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(forum);
                abstractInsertEventSerializer.changePartition();
            }
        }

    }

    public void export(final Post post) throws IOException {
        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(post);
        } else {
            if ((post.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (post.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            post.getDeletionDate() <= Dictionaries.dates.getSimulationEnd())
                    )) {
                dynamicActivitySerializer.export(post);
                if (post.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(post);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (post.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && post.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
                    ) {
                dynamicActivitySerializer.export(post);
            } else if (post.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (post.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    post.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(post);
                abstractInsertEventSerializer.changePartition();
                if (post.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(post);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (post.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && post.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(post);
                abstractInsertEventSerializer.changePartition();
            }
        }

    }

    public void export(final Comment comment) throws IOException {
        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(comment);
        } else {
         if ((comment.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (comment.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            comment.getDeletionDate() <= Dictionaries.dates.getSimulationEnd())
                    )) {
                dynamicActivitySerializer.export(comment);
                if (comment.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(comment);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (comment.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && comment.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
                    ) {
                dynamicActivitySerializer.export(comment);
            } else if (comment.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (comment.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    comment.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(comment);
                abstractInsertEventSerializer.changePartition();
                if (comment.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(comment);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (comment.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && comment.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(comment);
                abstractInsertEventSerializer.changePartition();
            }
        }
    }

    public void export(final Photo photo) throws IOException {
        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(photo);
        } else {
            if ((photo.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (photo.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            photo.getDeletionDate() <= Dictionaries.dates.getSimulationEnd())
            )) {
                dynamicActivitySerializer.export(photo);
                if (photo.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(photo);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (photo.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && photo.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
            ) {
                dynamicActivitySerializer.export(photo);
            } else if (photo.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (photo.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    photo.getDeletionDate() <= Dictionaries.dates.getSimulationEnd() ) {
                abstractInsertEventSerializer.export(photo);
                abstractInsertEventSerializer.changePartition();
                if (photo.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(photo);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (photo.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && photo.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(photo);
                abstractInsertEventSerializer.changePartition();
            }
        }
    }

    public void export(final ForumMembership member) throws IOException {

        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(member);
        } else {
            if ((member.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (member.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            member.getDeletionDate() <= Dictionaries.dates.getSimulationEnd())
                    )) {
                dynamicActivitySerializer.export(member);
                if (member.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(member);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (member.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && member.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
                    ) {
                dynamicActivitySerializer.export(member);
            } else if (member.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (member.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    member.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(member);
                abstractInsertEventSerializer.changePartition();
                if (member.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(member);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (member.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && member.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(member);
                abstractInsertEventSerializer.changePartition();
            }
        }
    }

    public void export(final Like like) throws IOException {

        if (DatagenParams.getDatagenMode() == DatagenMode.RAW_DATA){
            dynamicActivitySerializer.export(like);
        } else {
           if ((like.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold() &&
                    (like.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold() &&
                            like.getDeletionDate() <= Dictionaries.dates.getSimulationEnd())
            )) {
                dynamicActivitySerializer.export(like);
                if (like.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(like);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (like.getCreationDate() < Dictionaries.dates.getBulkLoadThreshold()
                    && like.getDeletionDate() > Dictionaries.dates.getSimulationEnd()
            ) {
                dynamicActivitySerializer.export(like);
            } else if (like.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && (like.getDeletionDate() >= Dictionaries.dates.getBulkLoadThreshold()) &&
                    like.getDeletionDate() <= Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(like);
                abstractInsertEventSerializer.changePartition();
                if (like.isExplicitlyDeleted()) {
                    abstractDeleteEventSerializer.export(like);
                    abstractDeleteEventSerializer.changePartition();
                }
            } else if (like.getCreationDate() >= Dictionaries.dates.getBulkLoadThreshold()
                    && like.getDeletionDate() > Dictionaries.dates.getSimulationEnd()) {
                abstractInsertEventSerializer.export(like);
                abstractInsertEventSerializer.changePartition();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (dynamicActivitySerializer != null) {
            dynamicActivitySerializer.close();
        }
        if (abstractInsertEventSerializer != null) {
            abstractInsertEventSerializer.close();
        }
        if (abstractDeleteEventSerializer != null) {
            abstractDeleteEventSerializer.close();
        }
    }
}
