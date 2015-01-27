/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2007-02-21 16:19:35 -0600 (Wed, 21 Feb 2007) $
 * $Revision: 6903 $
 *
 * Copyright (C) 2004-2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jmol.modelsetbio;

import javajs.util.Lst;

import org.jmol.c.STR;
import org.jmol.modelset.Atom;
import org.jmol.modelset.ModelSet;

import org.jmol.java.BS;
import org.jmol.util.Logger;

import javajs.util.Measure;
import javajs.util.P3;
import javajs.util.V3;

public class AlphaPolymer extends BioPolymer {

  AlphaPolymer(Monomer[] monomers) {
    set(monomers);
    hasStructure = true;
  }

  
  @Override
  public ProteinStructure getProteinStructure(int monomerIndex) {
    return (ProteinStructure) monomers[monomerIndex].getStructure();
  }

  @Override
  protected P3 getControlPoint(int i, V3 v) {
    if (!monomers[i].isSheet())
      return leadPoints[i];
    v.sub2(leadMidpoints[i], leadPoints[i]);
    v.scale(sheetSmoothing);
    P3 pt = P3.newP(leadPoints[i]);
    pt.add(v);
    return pt;
  }

  public void addStructure(STR type, String structureID,
                           int serialID, int strandCount, int startChainID,
                           int startSeqcode, int endChainID, int endSeqcode,
                           int istart, int iend, BS bsAssigned) {
    int i0 = -1;
    int i1 = -1;
    if (istart < iend) {
        if (monomers[0].firstAtomIndex > iend || monomers[monomerCount - 1].lastAtomIndex < istart)
      return;
     i0 = istart;
     i1 = iend;
    }
    int indexStart, indexEnd;
    if ((indexStart = getIndex(startChainID, startSeqcode, i0, i1)) == -1
        || (indexEnd = getIndex(endChainID, endSeqcode, i0, i1)) == -1)
      return;
    if (/*type != STR.ANNOTATION &&*/ istart >= 0 && bsAssigned != null) {
      int pt = bsAssigned.nextSetBit(monomers[indexStart].firstAtomIndex);
      if (pt >= 0 && pt < monomers[indexEnd].lastAtomIndex)
        return;
    }
    if (addStructureProtected(type, structureID, serialID, strandCount, indexStart,
        indexEnd) && istart >= 0)
      bsAssigned.setBits(istart, iend + 1);
  }

  public boolean addStructureProtected(STR type, 
                             String structureID, int serialID, int strandCount,
                             int indexStart, int indexEnd) {

    //these two can be the same if this is a carbon-only polymer
    if (indexEnd < indexStart) {
      Logger.error("AlphaPolymer:addSecondaryStructure error: " +
                         " indexStart:" + indexStart +
                         " indexEnd:" + indexEnd);
      return false;
    }
    int structureCount = indexEnd - indexStart + 1;
    ProteinStructure ps = null;
//    boolean isAnnotation = false;
    switch(type) {
//    case ANNOTATION:
//      ps = new Annotation(this, indexStart, structureCount, structureID);
//      isAnnotation = true;
//      break;
    case HELIX:
    case HELIXALPHA:
    case HELIX310:
    case HELIXPI:
      ps = new Helix(this, indexStart, structureCount, type);
      break;
    case SHEET:
      ps = new Sheet(this, indexStart, structureCount, type);
      break;
    case TURN:
      ps = new Turn(this, indexStart, structureCount);
      break;
    default:
      Logger.error("unrecognized secondary structure type");
      return false;
    }
    ps.structureID = structureID;
    ps.serialID = serialID;
    ps.strandCount = strandCount;
      for (int i = indexStart; i <= indexEnd; ++i) {
        ((AlphaMonomer)monomers[i]).setStructure(ps);//, isAnnotation);
    }
    return true;
  }
  
  @Override
  public void clearStructures() {
    for (int i = 0; i < monomerCount; i++)
      ((AlphaMonomer)monomers[i]).setStructure(null);//, false);
  }

  ///////////////////////////////////////////////////////////
  //
  // Struts calculation (for rapid prototyping)
  //
  ///////////////////////////////////////////////////////////
  /**
   * 
   * Algorithm of George Phillips phillips@biochem.wisc.edu
   * 
   * originally a contribution to pyMol as struts.py; adapted here by Bob Hanson
   * for Jmol 1/2010
   * 
   * Return a vector of support posts for rapid prototyping models along the
   * lines of George Phillips for Pymol except on actual molecular segments
   * (biopolymers), not PDB chains (which may or may not be continuous).
   * 
   * Like George, we go from thresh-4 to thresh in units of 1 Angstrom, but we
   * do not require this threshold to be an integer. In addition, we prevent
   * double-creation of struts by tracking where struts are, and we do not look
   * for any addtional end struts if there is a strut already to an atom at a
   * particular biopolymer end. The three parameters are:
   * 
   * set strutDefaultRadius 0.3 set strutSpacingMinimum 6 set strutLengthMaximum
   * 7.0
   * 
   * Struts will be introduced by:
   * 
   * calculate struts {atom set A} {atom set B}
   * 
   * where the two atom sets are optional and default to the currently selected
   * set.
   * 
   * They can be manipulated using the STRUTS command much like any "bond"
   * 
   * struts 0.3 color struts opaque pink connect {atomno=3} {atomno=4} strut
   * 
   * struts only
   * 
   * command
   * 
   * @param modelSet
   * @param bs1
   * @param bs2
   * @param vCA
   * @param thresh
   * @param delta
   * @param allowMultiple
   * @return vector of pairs of atoms
   * 
   */
  @Override
  public Lst<Atom[]> calculateStruts(ModelSet modelSet, BS bs1,
                                      BS bs2, Lst<Atom> vCA, float thresh,
                                      int delta, boolean allowMultiple) {
    return calculateStrutsStatic(modelSet, bs1, bs2, vCA, thresh, delta,
        allowMultiple);
  }
    
  private Lst<Atom[]> calculateStrutsStatic(ModelSet modelSet, BS bs1, BS bs2,
                                             Lst<Atom> vCA, float thresh,
                                             int delta, boolean allowMultiple) {
    Lst<Atom[]> vStruts = new  Lst<Atom[]>(); // the output vector
    float thresh2 = thresh * thresh; // use distance squared for speed

    int n = vCA.size();  // the set of alpha carbons
    int nEndMin = 3;

    // We set bitsets that indicate that there is no longer any need to
    // check for a strut. We are tracking both individual atoms (bsStruts) and
    // pairs of atoms (bsNotAvailable and bsNearbyResidues)
    
    BS bsStruts = new BS();         // [i]
    BS bsNotAvailable = new BS();   // [ipt]
    BS bsNearbyResidues = new BS(); // [ipt]
    
    // check for a strut. We are going to set struts within 3 residues
    // of the ends of biopolymers, so we track those positions as well.
    
    Atom a1 = vCA.get(0);
    Atom a2;
    int nBiopolymers = modelSet.getBioPolymerCountInModel(a1.mi);
    int[][] biopolymerStartsEnds = new int[nBiopolymers][nEndMin * 2];
    for (int i = 0; i < n; i++) {
      a1 = vCA.get(i);
      int polymerIndex = a1.group.getBioPolymerIndexInModel();
      int monomerIndex = a1.group.getMonomerIndex();
      int bpt = monomerIndex;
      if (bpt < nEndMin)
        biopolymerStartsEnds[polymerIndex][bpt] = i + 1;
      bpt = ((Monomer) a1.group).getBioPolymerLength() - monomerIndex - 1;
      if (bpt < nEndMin)
        biopolymerStartsEnds[polymerIndex][nEndMin + bpt] = i + 1;
    }

    // Get all distances.
    // For n CA positions, there will be n(n-1)/2 distances needed.
    // There is no need for a full matrix X[i][j]. Instead, we just count
    // carefully using the variable ipt:
    //
    // ipt = i * (2 * n - i - 1) / 2 + j - i - 1

    float[] d2 = new float[n * (n - 1) / 2];
    for (int i = 0; i < n; i++) {
      a1 = vCA.get(i);
      for (int j = i + 1; j < n; j++) {
        int ipt = strutPoint(i, j, n);
        a2 = vCA.get(j);
        int resno1 = a1.getResno();
        int polymerIndex1 = a1.group.getBioPolymerIndexInModel();
        int resno2 = a2.getResno();
        int polymerIndex2 = a2.group.getBioPolymerIndexInModel();
        if (polymerIndex1 == polymerIndex2 && Math.abs(resno2 - resno1) < delta)
          bsNearbyResidues.set(ipt);
        float d = d2[ipt] = a1.distanceSquared(a2);
        if (d >= thresh2)
          bsNotAvailable.set(ipt);
      }
    }

    // Now go through 5 spheres leading up to the threshold
    // in 1-Angstrom increments, picking up the shortest distances first

    for (int t = 5; --t >= 0;) { // loop starts with 4
      thresh2 = (thresh - t) * (thresh - t);
      for (int i = 0; i < n; i++)
        if (allowMultiple || !bsStruts.get(i))
        for (int j = i + 1; j < n; j++) {
          int ipt = strutPoint(i, j, n);
          if (!bsNotAvailable.get(ipt) && !bsNearbyResidues.get(ipt)
              && (allowMultiple || !bsStruts.get(j)) && d2[ipt] <= thresh2)
            setStrut(i, j, n, vCA, bs1, bs2, vStruts, bsStruts, bsNotAvailable,
                bsNearbyResidues, delta);
        }
    }

    // Now find a strut within nEndMin (3) residues of the end in each
    // biopolymer, but only if it is within one of the "not allowed"
    // regions - this is to prevent dangling ends to be connected by a
    // very long connection

    for (int b = 0; b < nBiopolymers; b++) {
      // if there are struts already in this area, skip this part
      for (int k = 0; k < nEndMin * 2; k++) {
        int i = biopolymerStartsEnds[b][k] - 1;
        if (i >= 0 && bsStruts.get(i)) {
          for (int j = 0; j < nEndMin; j++) {
            int pt = (k / nEndMin) * nEndMin + j;
            if ((i = biopolymerStartsEnds[b][pt] - 1) >= 0)
              bsStruts.set(i);
            biopolymerStartsEnds[b][pt] = -1;
          }
        }
      }
      if (biopolymerStartsEnds[b][0] == -1 && biopolymerStartsEnds[b][nEndMin] == -1)
        continue;
      boolean okN = false;
      boolean okC = false;
      int iN = 0;
      int jN = 0;
      int iC = 0;
      int jC = 0;
      float minN = Float.MAX_VALUE;
      float minC = Float.MAX_VALUE;
      for (int j = 0; j < n; j++)
        for (int k = 0; k < nEndMin * 2; k++) {
          int i = biopolymerStartsEnds[b][k] - 1;
          if (i == -2) {
            // skip all
            k = (k / nEndMin + 1) * nEndMin - 1;
            continue;
          }
          if (j == i || i == -1)
            continue;
          int ipt = strutPoint(i, j, n);
          if (bsNearbyResidues.get(ipt)
              || d2[ipt] > (k < nEndMin ? minN : minC))
            continue;
          if (k < nEndMin) {
            if (bsNotAvailable.get(ipt))
              okN = true;
            jN = j;
            iN = i;
            minN = d2[ipt];
          } else {
            if (bsNotAvailable.get(ipt))
              okC = true;
            jC = j;
            iC = i;
            minC = d2[ipt];
          }
        }
      if (okN)
        setStrut(iN, jN, n, vCA, bs1, bs2, vStruts, bsStruts, bsNotAvailable,
            bsNearbyResidues, delta);
      if (okC)
        setStrut(iC, jC, n, vCA, bs1, bs2, vStruts, bsStruts, bsNotAvailable,
            bsNearbyResidues, delta);
    }
    return vStruts;
  }

  private static int strutPoint(int i, int j, int n) {
    return (j < i ? j * (2 * n - j - 1) / 2 + i - j - 1
     : i * (2 * n - i - 1) / 2 + j - i - 1);
  }

  private static void setStrut(int i, int j, int n, Lst<Atom> vCA, BS bs1, BS bs2, 
                        Lst<Atom[]> vStruts,
                        BS bsStruts, BS bsNotAvailable,
                        BS bsNearbyResidues, int delta) {
    Atom a1 = vCA.get(i);
    Atom a2 = vCA.get(j);
    if (!bs1.get(a1.i) || !bs2.get(a2.i))
      return;
    vStruts.addLast(new Atom[] { a1, a2 });
    bsStruts.set(i);
    bsStruts.set(j);
    for (int k1 = Math.max(0, i - delta); k1 <= i + delta && k1 < n; k1++) {
      for (int k2 = Math.max(0, j - delta); k2 <= j + delta && k2 < n; k2++) {
        if (k1 == k2) {
          continue;
        }
        int ipt = strutPoint(k1, k2, n);
        if (!bsNearbyResidues.get(ipt)) {
          bsNotAvailable.set(ipt);
        }
      }
    }
  }
  
  ////////////////////////////////////////////////////////////
  //
  //  alpha-carbon-only secondary structure determination
  //
  //  Levitt and Greer
  //  Automatic Identification of Secondary Structure in Globular Proteins
  //  J.Mol.Biol.(1977) 114, 181-293
  //
  /////////////////////////////////////////////////////////////
  
  /**
   * Uses Levitt & Greer algorithm to calculate protein secondary
   * structures using only alpha-carbon atoms.
   *<p>
   * Levitt and Greer <br />
   * Automatic Identification of Secondary Structure in Globular Proteins <br />
   * J.Mol.Biol.(1977) 114, 181-293 <br />
   *<p>
   * <a
   * href='http://csb.stanford.edu/levitt/Levitt_JMB77_Secondary_structure.pdf'>
   * http://csb.stanford.edu/levitt/Levitt_JMB77_Secondary_structure.pdf
   * </a>
   * @param alphaOnly  caught by AminoPolymer and discarded if desired 
   */
  public void calculateStructures(boolean alphaOnly) { 
    if (monomerCount < 4)
      return;
    float[] angles = calculateAnglesInDegrees();
    Code[] codes = calculateCodes(angles);
    checkBetaSheetAlphaHelixOverlap(codes, angles);
    STR[] tags = calculateRunsFourOrMore(codes);
    extendRuns(tags);
    searchForTurns(codes, angles, tags);
    addStructuresFromTags(tags);
  }

  private enum Code {
    NADA, RIGHT_HELIX, BETA_SHEET, LEFT_HELIX, LEFT_TURN, RIGHT_TURN;
  }
  
  private float[] calculateAnglesInDegrees() {
    float[] angles = new float[monomerCount];
    for (int i = monomerCount - 1; --i >= 2; )
      angles[i] = 
        Measure.computeTorsion(monomers[i - 2].getLeadAtom(),
                                   monomers[i - 1].getLeadAtom(),
                                   monomers[i    ].getLeadAtom(),
                                   monomers[i + 1].getLeadAtom(), true);
    return angles;
  }

  private Code[] calculateCodes(float[] angles) {
    Code[] codes = new Code[monomerCount];
    for (int i = monomerCount - 1; --i >= 2; ) {
      float degrees = angles[i];
      codes[i] = ((degrees >= 10 && degrees < 120)
                  ? Code.RIGHT_HELIX
                  : ((degrees >= 120 || degrees < -90)
                     ? Code.BETA_SHEET
                     : ((degrees >= -90 && degrees < 0)
                        ? Code.LEFT_HELIX
                        : Code.NADA)));
    }
    return codes;
  }

  private void checkBetaSheetAlphaHelixOverlap(Code[] codes, float[] angles) {
    for (int i = monomerCount - 2; --i >= 2; )
      if (codes[i] == Code.BETA_SHEET &&
          angles[i] <= 140 &&
          codes[i - 2] == Code.RIGHT_HELIX &&
          codes[i - 1] == Code.RIGHT_HELIX &&
          codes[i + 1] == Code.RIGHT_HELIX &&
          codes[i + 2] == Code.RIGHT_HELIX)
        codes[i] = Code.RIGHT_HELIX;
  }

  private STR[] calculateRunsFourOrMore(Code[] codes) {
    STR[] tags = new STR[monomerCount];
    STR tag = STR.NONE;
    Code code = Code.NADA;
    int runLength = 0;
    for (int i = 0; i < monomerCount; ++i) {
      // throw away the sheets ... their angle technique does not work well
      if (codes[i] == code && code != Code.NADA && code != Code.BETA_SHEET) {
        ++runLength;
        if (runLength == 4) {
          tag = (code == Code.BETA_SHEET ? STR.SHEET : STR.HELIX);
          for (int j = 4; --j >= 0; )
            tags[i - j] = tag;
        } else if (runLength > 4)
          tags[i] = tag;
      } else {
        runLength = 1;
        code = codes[i];
      }
    }
    return tags;
  }

  private void extendRuns(STR[] tags) {
    for (int i = 1; i < monomerCount - 4; ++i)
      if (tags[i] == STR.NONE && tags[i + 1] != STR.NONE)
        tags[i] = tags[i + 1];
    
    tags[0] = tags[1];
    tags[monomerCount - 1] = tags[monomerCount - 2];
  }

  private void searchForTurns(Code[] codes, float[] angles, STR[] tags) {
    for (int i = monomerCount - 1; --i >= 2; ) {
      codes[i] = Code.NADA;
      if (tags[i] == null || tags[i] == STR.NONE) {
        float angle = angles[i];
        if (angle >= -90 && angle < 0)
          codes[i] = Code.LEFT_TURN;
        else if (angle >= 0 && angle < 90)
          codes[i] = Code.RIGHT_TURN;
      }
    }

    for (int i = monomerCount - 1; --i >= 0; ) {
      if (codes[i] != Code.NADA &&
          codes[i + 1] == codes[i] &&
          tags[i] == STR.NONE)
        tags[i] = STR.TURN;
    }
  }

  private void addStructuresFromTags(STR[] tags) {
    int i = 0;
    while (i < monomerCount) {
      STR tag = tags[i];
      if (tag == null || tag == STR.NONE) {
        ++i;
        continue;
      }
      int iMax;
      for (iMax = i + 1;
           iMax < monomerCount && tags[iMax] == tag;
           ++iMax)
        { }
      addStructureProtected(tag, null, 0, 0, i, iMax - 1);
      i = iMax;
    }
  }

}
