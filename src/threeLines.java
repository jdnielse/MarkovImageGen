import ar.com.hjg.pngj.ImageLineInt;

public class threeLines {
	public  ImageLineInt[] three = new ImageLineInt[3];
	
	/**
	 * Add the given image line to the head of the queue
	 * @param newb - the image line to add
	 * @return the removed image line
	 */
	public  ImageLineInt add(ImageLineInt newb) {
		ImageLineInt oldie = remove();
		three[0] = newb;
		return oldie;
	}
	
	/**
	 * Removes the image line at the end of the list
	 * @return the removed image line
	 */
	public  ImageLineInt remove(){
		ImageLineInt oldOne = three[0];
		ImageLineInt oldTwo = three[1];
		ImageLineInt oldThree = three[2];
		
		three[0] = null;
		three[1] = oldOne;
		three[2] = oldTwo;
		return oldThree;
	}
	
	/**
	 * Tells if we have three non-null image lines
	 * 
	 * @return true if there are 3 non-null image lines
	 */
	public  boolean isFull() {
		for (ImageLineInt temp : three){
			if (temp == null) {
				return false;
			}
		}
		return true;
	}
	
}