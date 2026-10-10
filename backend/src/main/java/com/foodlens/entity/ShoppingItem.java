package com.foodlens.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShoppingItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JsonIgnore private User user;
    private String name;
    private double quantity;
    private String unit;
    @Builder.Default private boolean purchased=false;
}
