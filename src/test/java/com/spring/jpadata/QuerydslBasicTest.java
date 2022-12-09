package com.spring.jpadata;


import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.spring.jpadata.entity.Member;
import com.spring.jpadata.entity.QMember;
import com.spring.jpadata.entity.QTeam;
import com.spring.jpadata.entity.Team;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
    @DisplayName("JPQL로 작성한 테스트 코드")
    public void startJPQL() {
        Member findMember = em.createQuery("select m from Member m where m.username =:username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    @DisplayName("QueryDsl로 작성한 테스트 코드")
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
    @DisplayName("검색조건")
    public void search() {
        Member member1 = jpaQueryFactory.selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10))).fetchOne();


        assertThat(member1.getUsername()).isEqualTo("member1");
        assertThat(member1.getAge()).isEqualTo(10);
        assertThat(member1.getTeam().getName()).isEqualTo("teamA");
    }

    /**
     * 회원 정렬
     * 1. 회원 나이 (내림차순)
     * 2. 회원 이름 (올림차순)
     * 단 2에서 회원 이름이 없으면 마지막에 출려(nulls last)
     */
    @Test
    @DisplayName("QueryDsl 정렬")
    public void sort() {
        em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> memberList = jpaQueryFactory.selectFrom(member)
                .where(member.age.goe(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) //정렬
                .fetch();

        Member member5 = memberList.get(0);
        Member member6 = memberList.get(1);
        Member memberNull = memberList.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    @DisplayName("페이징 처리")
    public void paging() {
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> list = jpaQueryFactory.selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 0부터 시작
                .limit(2)
                .fetch();

        Member member5 = list.get(0);
        Member member4 = list.get(1);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member4.getUsername()).isEqualTo("member4");
    }

    @Test
    @DisplayName("집합 함수")
    public void aggregation() {
        //타입이 Tuple
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

    /**팀의 이름과 각 팀의 평균연령을 구해라*/
    @Test
    @DisplayName("join해서 집합함수 사용하기")
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

    //

}