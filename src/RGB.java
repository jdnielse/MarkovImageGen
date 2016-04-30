
public class RGB {
	@Override
	public String toString() {
		return "RGB [R=" + R + ", G=" + G + ", B=" + B + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + B;
		result = prime * result + G;
		result = prime * result + R;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RGB other = (RGB) obj;
		if (B != other.B)
			return false;
		if (G != other.G)
			return false;
		if (R != other.R)
			return false;
		return true;
	}

	public int R;
	public int G;
	public int B;
	
	public RGB(int R, int G, int B){
		this.R = R;
		this.G = G;
		this.B = B;
		
	}
	
	public RGB(){}

}
