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
package ldbc.snb.datagen.serializer.snb.csv.dynamicserializer.person;

import com.google.common.collect.ImmutableList;
import ldbc.snb.datagen.dictionary.Dictionaries;
import ldbc.snb.datagen.entities.dynamic.person.Person;
import ldbc.snb.datagen.entities.dynamic.relations.Knows;
import ldbc.snb.datagen.entities.dynamic.relations.StudyAt;
import ldbc.snb.datagen.entities.dynamic.relations.WorkAt;
import ldbc.snb.datagen.hadoop.writer.HdfsCsvWriter;
import ldbc.snb.datagen.serializer.DynamicPersonSerializer;
import ldbc.snb.datagen.serializer.snb.csv.CsvSerializer;
import ldbc.snb.datagen.serializer.snb.csv.FileName;

import java.util.Iterator;
import java.util.List;

import static ldbc.snb.datagen.serializer.snb.csv.FileName.*;

public class CsvMergeForeignDynamicPersonSerializer extends DynamicPersonSerializer<HdfsCsvWriter> implements CsvSerializer {

    @Override
    public List<FileName> getFileNames() {
        return ImmutableList.of(PERSON, PERSON_SPEAKS_LANGUAGE, PERSON_HAS_EMAIL, PERSON_HAS_INTEREST_TAG,
                PERSON_WORK_AT, PERSON_STUDY_AT, PERSON_KNOWS_PERSON);
    }

    @Override
    public void writeFileHeaders() {
        writers.get(PERSON).writeHeader(ImmutableList.of("id", "firstName", "lastName", "gender", "birthday", "creationDate", "locationIP", "browserUsed", "place"));
        writers.get(PERSON_SPEAKS_LANGUAGE).writeHeader(ImmutableList.of("Person.id", "language", "creationDate"));
        writers.get(PERSON_HAS_EMAIL).writeHeader(ImmutableList.of("Person.id", "email", "creationDate"));
        writers.get(PERSON_HAS_INTEREST_TAG).writeHeader(ImmutableList.of("Person.id", "Tag.id", "creationDate"));

        writers.get(PERSON_STUDY_AT).writeHeader(ImmutableList.of("Person.id", "Organisation.id", "classYear", "creationDate"));
        writers.get(PERSON_WORK_AT).writeHeader(ImmutableList.of("Person.id", "Organisation.id", "workFrom", "creationDate"));

        writers.get(PERSON_KNOWS_PERSON).writeHeader(ImmutableList.of("Person.id", "Person.id", "creationDate"));
    }

    @Override
    protected void serialize(final Person p) {
        String dateString = Dictionaries.dates.formatDateTime(p.creationDate());

        //"id", "firstName", "lastName", "gender", "birthday", "creationDate", "locationIP", "browserUsed", "place"
        writers.get(PERSON).writeEntry(ImmutableList.of(
            Long.toString(p.accountId()),
            p.firstName(),
            p.lastName(),
            getGender(p.gender()),
            Dictionaries.dates.formatDate(p.birthday()),
            dateString,
            p.ipAddress().toString(),
            Dictionaries.browsers.getName(p.browserId()),
            Integer.toString(p.cityId())
        ));

        List<Integer> languages = p.languages();
        for (int i = 0; i < languages.size(); i++) {
            //"Person.id", "language", "creationDate"
            writers.get(PERSON_SPEAKS_LANGUAGE).writeEntry(ImmutableList.of(
                Long.toString(p.accountId()),
                Dictionaries.languages.getLanguageName(languages.get(i)),
                dateString
            ));
        }

        Iterator<String> emails = p.emails().iterator();
        while (emails.hasNext()) {
            //"Person.id", "email", "creationDate"
            writers.get(PERSON_HAS_EMAIL).writeEntry(ImmutableList.of(
                Long.toString(p.accountId()),
                emails.next(),
                dateString
            ));
        }

        Iterator<Integer> interests = p.interests().iterator();
        while (interests.hasNext()) {
            //"Person.id", "Tag.id", "creationDate"
            writers.get(PERSON_HAS_INTEREST_TAG).writeEntry(ImmutableList.of(
                Long.toString(p.accountId()),
                Integer.toString(interests.next()),
                dateString
            ));
        }
    }

    @Override
    protected void serialize(final StudyAt studyAt,final Person person) {
        //"Person.id", "Organisation.id", "classYear", "creationDate"
        writers.get(PERSON_STUDY_AT).writeEntry(ImmutableList.of(
            Long.toString(studyAt.user),
            Long.toString(studyAt.university),
            Dictionaries.dates.formatYear(studyAt.year),
            Dictionaries.dates.formatDateTime(person.creationDate())
        ));
    }

    @Override
    protected void serialize(final WorkAt workAt,final Person person) {
        //"Person.id", "Organisation.id", "workFrom", "creationDate"
        writers.get(PERSON_WORK_AT).writeEntry(ImmutableList.of(
            Long.toString(workAt.user),
            Long.toString(workAt.company),
            Dictionaries.dates.formatYear(workAt.year),
            Dictionaries.dates.formatDateTime(person.creationDate())
        ));
    }

    @Override
    protected void serialize(final Person p, Knows knows) {
        //"Person.id", "Person.id", "creationDate"
        writers.get(PERSON_KNOWS_PERSON).writeEntry(ImmutableList.of(
            Long.toString(p.accountId()),
            Long.toString(knows.to().accountId()),
            Dictionaries.dates.formatDateTime(knows.creationDate())
        ));
    }

}