/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.isis.core.metamodel.services.appfeat;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

import org.apache.isis.applib.annotation.Programmatic;
import org.apache.isis.applib.annotation.Value;
import org.apache.isis.applib.services.appfeat.ApplicationMemberType;
import org.apache.isis.applib.util.ObjectContracts;
import org.apache.isis.applib.util.TitleBuffer;

/**
 * Value type representing a package, class or member.
 *
 * <p>
 *     This value is {@link Comparable}, the implementation of which considers {@link #getType() (feature) type},
 *     {@link #getPackageName() package name}, {@link #getClassName() class name} and {@link #getMemberName() member name}.
 * </p>
 */
@Value
public class ApplicationFeatureId implements Comparable<ApplicationFeatureId>, Serializable {


    // //////////////////////////////////////

    //region > factory methods
    public static ApplicationFeatureId newFeature(final ApplicationFeatureType featureType, final String fullyQualifiedName) {
        switch (featureType) {
            case PACKAGE:
                return newPackage(fullyQualifiedName);
            case CLASS:
                return newClass(fullyQualifiedName);
            case MEMBER:
                return newMember(fullyQualifiedName);
        }
        throw new IllegalArgumentException("Unknown feature type " + featureType);
    }

    public static ApplicationFeatureId newFeature(
            final String packageFqn, final String className, final String memberName) {
        if(className == null) {
            return newPackage(packageFqn);
        }
        final String classFqn = packageFqn + "." + className;
        if(memberName == null) {
            return newClass(classFqn);
        }
        return newMember(classFqn, memberName);
    }

    public static ApplicationFeatureId newPackage(final String packageFqn) {
        final ApplicationFeatureId featureId = new ApplicationFeatureId(ApplicationFeatureType.PACKAGE);
        featureId.setPackageName(packageFqn);
        return featureId;
    }

    public static ApplicationFeatureId newClass(final String classFqn) {
        final ApplicationFeatureId featureId = new ApplicationFeatureId(ApplicationFeatureType.CLASS);
        featureId.type.init(featureId, classFqn);
        return featureId;
    }

    public static ApplicationFeatureId newMember(final String classFqn, final String memberName) {
        final ApplicationFeatureId featureId = new ApplicationFeatureId(ApplicationFeatureType.MEMBER);
        ApplicationFeatureType.CLASS.init(featureId, classFqn);
        featureId.type = ApplicationFeatureType.MEMBER;
        featureId.setMemberName(memberName);
        return featureId;
    }

    public static ApplicationFeatureId newMember(final String fullyQualifiedName) {
        final ApplicationFeatureId featureId = new ApplicationFeatureId(ApplicationFeatureType.MEMBER);
        featureId.type.init(featureId, fullyQualifiedName);
        return featureId;
    }

    /**
     * Round-trip with {@link #asString()}
     */
    public static ApplicationFeatureId parse(final String asString) {
        return new ApplicationFeatureId(asString);
    }

    /**
     * Round-trip with {@link #asEncodedString()}
     */
    public static ApplicationFeatureId parseEncoded(final String encodedString) {
        return new ApplicationFeatureId(base64UrlDecode(encodedString));
    }
    //endregion

    // //////////////////////////////////////

    //region > constructor

    private ApplicationFeatureId(final String asString) {
        final Iterator<String> iterator = Splitter.on(":").split(asString).iterator();
        final ApplicationFeatureType type = ApplicationFeatureType.valueOf(iterator.next());
        type.init(this, iterator.next());
    }

    /**
     * Must be called by {@link ApplicationFeatureType#init(ApplicationFeatureId, String)} immediately afterwards
     * to fully initialize.
     */
    ApplicationFeatureId(final ApplicationFeatureType type) {
        this.type = type;
    }

    public ApplicationFeatureId(final ApplicationFeatureType type, final String fullyQualifiedName) {
        type.init(this, fullyQualifiedName);
    }

    //endregion

    // //////////////////////////////////////

    //region > identification
    /**
     * having a title() method (rather than using @Title annotation) is necessary as a workaround to be able to use
     * wrapperFactory#unwrap(...) method, which is otherwise broken in Isis 1.6.0
     */
    public String title() {
        final TitleBuffer buf = new TitleBuffer();
        buf.append(getFullyQualifiedName());
        return buf.toString();
    }
    //endregion

    // //////////////////////////////////////

    //region > fullyQualifiedName (property)

    @Programmatic
    public String getFullyQualifiedName() {
        final StringBuilder buf = new StringBuilder();
        buf.append(getPackageName());
        if(getClassName() != null) {
            buf.append(".").append(getClassName());
        }
        if(getMemberName() != null) {
            buf.append("#").append(getMemberName());
        }
        return buf.toString();
    }

    //endregion

    // //////////////////////////////////////

    //region > type (property)
    ApplicationFeatureType type;

    public ApplicationFeatureType getType() {
        return type;
    }
    //endregion

    // //////////////////////////////////////

    //region > packageName (property)
    private String packageName;

    @Programmatic
    public String getPackageName() {
        return packageName;
    }

    void setPackageName(final String packageName) {
        this.packageName = packageName;
    }
    //endregion

    // //////////////////////////////////////

    //region > className (property, optional)

    private String className;

    @Programmatic
    public String getClassName() {
        return className;
    }

    void setClassName(final String className) {
        this.className = className;
    }
    //endregion

    // //////////////////////////////////////

    //region > memberName (property, optional)
    private String memberName;

    @Programmatic
    public String getMemberName() {
        return memberName;
    }

    void setMemberName(final String memberName) {
        this.memberName = memberName;
    }
    //endregion

    // //////////////////////////////////////

    //region > Package or Class: getParentPackageId

    /**
     * The {@link ApplicationFeatureId id} of the parent package of this
     * class or package.
     */
    @Programmatic
    public ApplicationFeatureId getParentPackageId() {
        ApplicationFeatureType.ensurePackageOrClass(this);

        if(type == ApplicationFeatureType.CLASS) {
            return ApplicationFeatureId.newPackage(getPackageName());
        } else {
            final String packageName = getPackageName(); // eg aaa.bbb.ccc

            if(!packageName.contains(".")) {
                return null; // parent is root
            }

            final Iterable<String> split = Splitter.on(".").split(packageName);
            final List<String> parts = Lists.newArrayList(split); // eg [aaa,bbb,ccc]
            parts.remove(parts.size()-1); // remove last, eg [aaa,bbb]

            final String parentPackageName = Joiner.on(".").join(parts); // eg aaa.bbb

            return newPackage(parentPackageName);
        }
    }

    //endregion

    // //////////////////////////////////////

    //region > Member: getParentClassId

    /**
     * The {@link ApplicationFeatureId id} of the member's class.
     */
    public ApplicationFeatureId getParentClassId() {
        ApplicationFeatureType.ensureMember(this);
        final String classFqn = this.getPackageName() + "." + getClassName();
        return newClass(classFqn);
    }
    //endregion

    // //////////////////////////////////////

    //region > asString, asEncodedString

    @Programmatic
    public String asString() {
        return Joiner.on(":").join(type, getFullyQualifiedName());
    }

    @Programmatic
    public String asEncodedString() {
        return base64UrlEncode(asString());
    }

    private static String base64UrlDecode(final String str) {
        final byte[] bytes = BaseEncoding.base64Url().decode(str);
        return new String(bytes, Charset.forName("UTF-8"));
    }

    private static String base64UrlEncode(final String str) {
        final byte[] bytes = str.getBytes(Charset.forName("UTF-8"));
        return BaseEncoding.base64Url().encode(bytes);
    }



    //endregion

    // //////////////////////////////////////

    //region > Functions

    public static class Functions {

        private Functions(){}

        public static final Function<ApplicationFeatureId, String> GET_CLASS_NAME = new Function<ApplicationFeatureId, String>() {
            @Override
            public String apply(final ApplicationFeatureId input) {
                return input.getClassName();
            }
        };

        public static final Function<ApplicationFeatureId, String> GET_MEMBER_NAME = new Function<ApplicationFeatureId, String>() {
            @Override
            public String apply(final ApplicationFeatureId input) {
                return input.getMemberName();
            }
        };

    }
    //endregion

    // //////////////////////////////////////

    //region > Predicates

    public static class Predicates {
        private Predicates(){}

        public static Predicate<ApplicationFeatureId> isClassContaining(
                final ApplicationMemberType memberType, final ApplicationFeatureRepositoryDefault applicationFeatures) {
            return new Predicate<ApplicationFeatureId>() {
                @Override
                public boolean apply(final ApplicationFeatureId input) {
                    if(input.getType() != ApplicationFeatureType.CLASS) {
                        return false;
                    }
                    final ApplicationFeature feature = applicationFeatures.findFeature(input);
                    if(feature == null) {
                        return false;
                    }
                    return memberType == null || !feature.membersOf(memberType).isEmpty();
                }
            };
        }

        public static Predicate<ApplicationFeatureId> isClassRecursivelyWithin(final ApplicationFeatureId packageId) {
            return new Predicate<ApplicationFeatureId>() {
                @Override
                public boolean apply(final ApplicationFeatureId input) {
                    return input.getParentIds().contains(packageId);
                }
            };
        }
    }
    //endregion

    // //////////////////////////////////////

    //region > Comparators
    public static final class Comparators {
        private Comparators(){}
        public static Comparator<ApplicationFeatureId> natural() {
            return new ApplicationFeatureIdComparator();
        }

        static class ApplicationFeatureIdComparator implements Comparator<ApplicationFeatureId>, Serializable {

            @Override
            public int compare(final ApplicationFeatureId o1, final ApplicationFeatureId o2) {
                return o1.compareTo(o2);
            }
        }
    }
    //endregion

    // //////////////////////////////////////

    //region > pathIds, parentIds

    @Programmatic
    public List<ApplicationFeatureId> getPathIds() {
        return pathIds(this);
    }

    @Programmatic
    public List<ApplicationFeatureId> getParentIds() {
        return pathIds(getParentId());
    }

    private ApplicationFeatureId getParentId() {
        return type == ApplicationFeatureType.MEMBER? getParentClassId(): getParentPackageId();
    }

    private static List<ApplicationFeatureId> pathIds(final ApplicationFeatureId id) {
        final List<ApplicationFeatureId> featureIds = Lists.newArrayList();
        return Collections.unmodifiableList(appendParents(id, featureIds));
    }

    private static List<ApplicationFeatureId> appendParents(final ApplicationFeatureId featureId, final List<ApplicationFeatureId> parentIds) {
        if(featureId != null) {
            parentIds.add(featureId);
            appendParents(featureId.getParentId(), parentIds);
        }
        return parentIds;
    }
    //endregion

    // //////////////////////////////////////

    //region > equals, hashCode, compareTo, toString

    private final static String propertyNames = "type, packageName, className, memberName";

    @Override
    public int compareTo(final ApplicationFeatureId other) {
        return ObjectContracts.compare(this, other, propertyNames);
    }

    @Override
    public boolean equals(final Object o) {

        // not using our ObjectContracts helper because trying to be efficient.  Premature optimization?
        // return ObjectContracts.equals(this, obj, propertyNames);

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ApplicationFeatureId that = (ApplicationFeatureId) o;

        if (className != null ? !className.equals(that.className) : that.className != null) return false;
        if (memberName != null ? !memberName.equals(that.memberName) : that.memberName != null) return false;
        if (packageName != null ? !packageName.equals(that.packageName) : that.packageName != null) return false;
        return type == that.type;

    }

    @Override
    public int hashCode() {
        // not using because trying to be efficient.  Premature optimization?
        // return ObjectContracts.hashCode(this, propertyNames);
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (packageName != null ? packageName.hashCode() : 0);
        result = 31 * result + (className != null ? className.hashCode() : 0);
        result = 31 * result + (memberName != null ? memberName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        switch (type) {
            case PACKAGE:
                return ObjectContracts.toString(this, "type, packageName");
            case CLASS:
                return ObjectContracts.toString(this, "type, packageName, className");
            case MEMBER:
                return ObjectContracts.toString(this, propertyNames);
        }
        throw new IllegalStateException("Unknown feature type " + type);
    }

    //endregion

}
