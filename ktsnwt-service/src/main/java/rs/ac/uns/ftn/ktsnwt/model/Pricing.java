package rs.ac.uns.ftn.ktsnwt.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "pricing")
public class Pricing {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price", nullable = false)
    private double price;

    @OneToOne
    @JoinColumn(name = "sector_id", referencedColumnName = "id", nullable = false)
    private Sector sector;

    public Pricing(){

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Sector getSector() {
        return sector;
    }

    public void setSector(Sector sector) {
        this.sector = sector;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj == null || getClass() != obj.getClass()) return  false;
        Pricing pricing = (Pricing) obj;
        return id.equals(pricing.id);
    }

    @Override
    public int hashCode(){
        return Objects.hash(id);
    }
}
