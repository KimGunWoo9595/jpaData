package com.spring.jpadata;


import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.spring.jpadata.dto.MemberDto;
import com.spring.jpadata.dto.MemberResponse;
import com.spring.jpadata.dto.QMemberResponse;
import com.spring.jpadata.entity.Member;

import com.spring.jpadata.entity.QMember;
import com.spring.jpadata.entity.QTeam;
import com.spring.jpadata.entity.Team;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;


import java.util.List;


import static com.spring.jpadata.entity.QMember.*;
import static com.spring.jpadata.entity.QTeam.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory jpaQueryFactory;

    @BeforeEach
    public void before() {
        jpaQueryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);
        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    @DisplayName("JPQL??? ????????? ????????? ??????")
    public void startJPQL() {
        Member findMember = em.createQuery("select m from Member m where m.username =:username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    @DisplayName("QueryDsl??? ????????? ????????? ??????")
    public void startDsl() {

        //import static com.spring.jpadata.entity.QMember.*;
        Member findMember = jpaQueryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();


        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    @DisplayName("????????????")
    public void search() {
        Member member1 = jpaQueryFactory.selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10))).fetchOne();


        assertThat(member1.getUsername()).isEqualTo("member1");
        assertThat(member1.getAge()).isEqualTo(10);
        assertThat(member1.getTeam().getName()).isEqualTo("teamA");
    }

    /**
     * ?????? ??????
     * 1. ?????? ?????? (????????????)
     * 2. ?????? ?????? (????????????)
     * ??? 2?????? ?????? ????????? ????????? ???????????? ??????(nulls last)
     */
    @Test
    @DisplayName("QueryDsl ??????")
    public void sort() {
        em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> memberList = jpaQueryFactory.selectFrom(member)
                .where(member.age.goe(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) //??????
                .fetch();

        Member member5 = memberList.get(0);
        Member member6 = memberList.get(1);
        Member memberNull = memberList.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    @DisplayName("????????? ??????")
    public void paging() {
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> list = jpaQueryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 0?????? ??????
                .limit(2)
                .fetch();

        Member member5 = list.get(0);
        Member member4 = list.get(1);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member4.getUsername()).isEqualTo("member4");
    }

    @Test
    @DisplayName("?????? ??????")
    public void aggregation() {
        //????????? Tuple
        List<Tuple> list = jpaQueryFactory.select(
                member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
        )
                .from(member)
                .fetch();

        Tuple tuple = list.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);


    }

    /**?????? ????????? ??? ?????? ??????????????? ?????????*/
    @Test
    @DisplayName("join?????? ???????????? ????????????")
    void group() throws Exception {

       List<Tuple> result = jpaQueryFactory
               .select(team.name, member.age.avg())
               .from(member)
               .join(member.team, team)
               .groupBy(team.name)
               .fetch();

       Tuple teamA = result.get(0);
       Tuple teamB = result.get(1);
       assertThat(teamA.get(team.name)).isEqualTo("teamA");
       assertThat(teamA.get(member.age.avg())).isEqualTo(15);
       assertThat(teamB.get(team.name)).isEqualTo("teamB");
       assertThat(teamB.get(member.age.avg())).isEqualTo(35);

    }

    @Test
    @DisplayName("join test")
    void join() throws Exception {

        List<Member> result = jpaQueryFactory.selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");

    }

    /**????????? ????????? ???????????? ?????? ?????? */
    @Test
    @DisplayName("theta ??????")
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = jpaQueryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * ???) ????????? ?????? ???????????????, ??? ????????? teamA??? ?????? ??????, ????????? ?????? ??????
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and
     t.name='teamA'
     */
    @Test
    @DisplayName("on??? ?????????")
   void join_on_filtering() throws Exception {

        List<Tuple> result = jpaQueryFactory.select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }

    @Test
    @DisplayName("??????????????? ??????????????? ??????")
    void innerJoin_and_outerJoin() throws Exception {

        List<Tuple> result = jpaQueryFactory.select(member, team)
                .from(member)
                .join(member.team, team)
                .on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

    }

    @Test
    @DisplayName("??????????????? ?????? ???????????? ????????????")
    void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        List<Tuple> result = jpaQueryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team) // ????????? ??? ?????? ????????? member.team
                //???????????? team??? ?????? ????????? ??? join?????? id??? ?????? x ???????????????
                .on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    @Test
    @DisplayName("?????? ?????? not fetchjoin")
    void join2() throws Exception {

        em.flush();
        em.clear();

        Member findMember = jpaQueryFactory
                .selectFrom(member)
                .join(member.team,team)
                .where(member.username.eq("member1"))
                .fetchOne();

    }

    @Test
    @DisplayName("fetch join ?????????")
    void fetch() throws Exception {

        em.flush();
        em.clear();

        Member findMember = jpaQueryFactory
                .selectFrom(member)
                .join(member.team,team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();


    }


    /**????????? ?????? ?????? ?????? ??????*/
    @Test
    @DisplayName("?????? ??????")
    void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = jpaQueryFactory.selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                )).fetch();

        assertThat(result).extracting("age").containsExactly(40);
    }

    @Test
    @DisplayName("????????? ?????? ????????? ?????? ??????")
    void subQuery2() throws Exception {
        QMember memberSub = new QMember("memberSub");
        // ?????? !!!!!!!!!!!! ?????????????????? ????????? member???????????? ??????
        List<Member> result = jpaQueryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        //25
        assertThat(result).extracting("age")
                .containsExactly(30, 40);


    }

    /***10??? ?????? in??? ??????*/
    @Test
    @DisplayName("???????????? in??? ?????? ")
    void inSub() throws Exception {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = jpaQueryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);

    }

    @Test
    @DisplayName("case??? ")
    void caseExcercise() throws Exception {
        List<String> result = jpaQueryFactory
                .select(member.age
                .when(10).then("10???")
                .when(20).then("20???")
                .otherwise("??????"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    @DisplayName("?????? ????????? concat")
    void concat() throws Exception {
        List<String> list = jpaQueryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()).concat("???"))
                .from(member)
                .fetch();

        for (String s : list) {
            System.out.println("member = " + s);
        }
    }
    
    @Test
    @DisplayName("??????")
    void getTuple () throws Exception {

        List<Tuple> result = jpaQueryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String userName = tuple.get(member.username);
            Integer userAge = tuple.get(member.age);
            System.out.println("userName = " + userName);
            System.out.println("userAge = " + userAge);
        }
    }

    @Test
    @DisplayName("jpql dto")
    void findtoByJpql () throws Exception {

        List<MemberResponse> memberResponseList =
                em.createQuery("select new com.spring.jpadata.dto.MemberResponse(m.username,m.age)" +
                " from Member m", MemberResponse.class).getResultList();

        for (MemberResponse memberResponse : memberResponseList) {
            System.out.println("memberResponse = " + memberResponse);
        }
    }

    @Test
    @DisplayName("?????????????????? dto setter??????")
    void dto() throws Exception {
        //dto??? ??????
        List<MemberResponse> result = jpaQueryFactory.select(
                Projections.bean(MemberResponse.class,
                        member.username,
                        member.age)
        ).from(member)
                .fetch();

        for (MemberResponse memberResponse : result) {
            System.out.println("memberResponse = " + memberResponse);
        }

    }

    @Test
    @DisplayName("?????????????????? dto ????????????")
    void dtoByField() throws Exception {
        //dto??? ??????
        List<MemberResponse> result = jpaQueryFactory.select(
                Projections.fields(MemberResponse.class,
                        member.username,
                        member.age)
        ).from(member)
                .fetch();

        for (MemberResponse memberResponse : result) {
            System.out.println("memberResponse = " + memberResponse);
        }

    }


    @Test
    @DisplayName("?????????????????? dto???????????????")
    void dtoByConstructer() throws Exception {
        //dto??? ??????
        List<MemberResponse> result = jpaQueryFactory.select(
                Projections.constructor(MemberResponse.class,
                        member.username,
                        member.age)
        ).from(member)
                .fetch();

        for (MemberResponse memberResponse : result) {
            System.out.println("memberResponse = " + memberResponse);
        }
    }

    @Test
    @DisplayName("@QueryProjection")
    void dtoByAnnotaion() throws Exception {

        //???????????? ????????? ????????? ??? ??????.
        List<MemberResponse> memberResponseList = jpaQueryFactory
                .select(new QMemberResponse(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberResponse memberResponse : memberResponseList) {
            System.out.println("memberResponse = " + memberResponse);
        }
    }

    @Test
    @DisplayName("@BooleanBulider ?????? ????????????")
    void dynamicQuery_BooleanBuilder() throws Exception {
        //?????? ??????
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> members = searchMember1(usernameParam, ageParam);

        assertThat(members.size()).isEqualTo(1);


    }

    private List<Member> searchMember1(String usernameCond,Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        //usernameCond ??? ?????? ????????? BooleanBuilder??? and ????????? ?????? ??? ?????????.
        if (StringUtils.hasText(usernameCond)) { // null??? ?????? ??????
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return jpaQueryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }


    @Test
    @DisplayName("Where ?????? ???????????? ?????? ")
    void dynamicQuery_WhereParam() throws Exception {

        //?????? ??????
        String usernameParam = "member2";
        Integer ageParam = 20;
        List<Member> members = searchMember2(usernameParam, ageParam);

        assertThat(members.size()).isEqualTo(1);


    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return jpaQueryFactory
                .selectFrom(member) // where??? null??? ????????? ?????? ???????????????. ????????? ??????????????? ???????????????
                .where(usernameEq(usernameCond),ageEq(ageCond)) // allEq(usernameCond,ageCond)
                .fetch();
    }


    //?????? : ????????????
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    private BooleanExpression usernameEq(String usernameCond) {
        //usernameCond??? null?????? null??? ??????
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        if(ageCond == null) return null;
        return member.age.eq(ageCond);
    }

    @Test
    @DisplayName("?????? ??????")
    void bulk() throws Exception {
        long count = jpaQueryFactory
                .update(member)
                .set(member.username, "?????????")
                .where(member.age.lt(28))
                .execute();

        //??????????????? ????????? ????????? ??????????????? ????????? ????????? ->????????? ??????????????? ??? ??????????????????!
        em.flush(); // ????????? ??????????????? ????????? ??? ?????????
        em.clear(); // ????????? ??????????????? ?????????

        //????????? ????????? ??????????????? ???????????? ??????
        List<Member> result = jpaQueryFactory.selectFrom(member)
                .fetch();

        for (Member mem : result) {
            System.out.println("mem = " + mem);
        }
    }


    @Test
    @DisplayName("????????? ?????????")
    void bulkAdd() throws Exception {
        //???????????? member.age.add(-1)
        //??????????????? member.age.multiply(2);

        jpaQueryFactory
                .update(member)
                .set(member.age, member.age.add(-1))
                .execute();
    }

    @Test
    @DisplayName("??????")
    void delete() throws Exception {
        long count = jpaQueryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();

        System.out.println("count = " + count);


    }

}
