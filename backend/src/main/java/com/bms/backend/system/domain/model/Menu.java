package com.bms.backend.system.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "tb_sys_menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @Column(name = "menu_id", length = 26, nullable = false)
    private String menuId;

    @Column(name = "up_menu_id", length = 26)
    private String parentMenuId;

    @Column(name = "menu_nm", length = 100, nullable = false)
    private String menuName;

    @Column(name = "menu_url", length = 300)
    private String menuUrl;

    @Column(name = "sort_seq", nullable = false)
    private Integer sortSequence;

    @Column(name = "reg_id", length = 26, nullable = false)
    private String registeredBy;

    @Column(name = "reg_dtm", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "mod_id", length = 26)
    private String modifiedBy;

    @Column(name = "mod_dtm")
    private LocalDateTime modifiedAt;

    @Column(name = "del_yn", nullable = false, columnDefinition = "char(1)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String deleted;
}
